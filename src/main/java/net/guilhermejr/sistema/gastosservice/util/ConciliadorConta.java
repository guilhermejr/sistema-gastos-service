package net.guilhermejr.sistema.gastosservice.util;

import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sugere, para cada transação do banco na conta corrente, o lançamento ou a movimentação
 * do sistema com que ela casa. Só sugere: quem liga os dois é o usuário, na revisão.
 *
 * <p>Ordem das tentativas:
 * <ol>
 *   <li>Aplicação e resgate da aplicação automática do Itaú ("APLIC AUT") → ignorar. O
 *   saldo do banco já soma a aplicação automática, então o vaivém entre ela e a conta não
 *   muda nada. O rendimento dela ("REND PAGO APLIC AUT") é dinheiro novo e segue o fluxo
 *   normal — sem par, vira sugestão de criar receita.</li>
 *   <li>Mesmo tipo e mesmo valor ao centavo, com até {@link #DIAS_MESMO_VALOR} dias de
 *   diferença (um fixo do dia 10 pago no dia 8). Quando há tantas transações quanto itens
 *   do sistema com o mesmo valor, casam em ordem de data; senão, o usuário escolhe.</li>
 *   <li>Lançamento com valor próximo (até {@link Conciliador#TOLERANCIA_REAIS} ou
 *   {@link Conciliador#TOLERANCIA_PERCENTUAL}) e até {@link #DIAS} dias → valor diferente,
 *   para corrigir ou manter. Movimentação não entra aqui: o valor dela é o do banco.</li>
 *   <li>O resto → criar lançamento.</li>
 * </ol>
 * Itens que não entraram em nenhuma sugestão voltam em {@code semPar}.
 */
public final class ConciliadorConta {

    static final int DIAS_MESMO_VALOR = 10;
    static final int DIAS = 5;

    private ConciliadorConta() {
    }

    /** Um lançamento ou uma movimentação da conta, do ponto de vista dela: D = saiu, R = entrou. */
    public record Item(Lancamento lancamento, Movimentacao movimentacao, TipoLancamento tipo, BigDecimal valor, LocalDate data) {

        public static Item de(Lancamento lancamento) {
            return new Item(lancamento, null, lancamento.getTipo(), lancamento.getValor(), lancamento.getData());
        }

        public static Item de(Movimentacao movimentacao, Conta conta) {
            boolean saiu = movimentacao.getContaOrigem() != null && movimentacao.getContaOrigem().getId().equals(conta.getId());
            return new Item(null, movimentacao, saiu ? TipoLancamento.D : TipoLancamento.R, movimentacao.getValor(), movimentacao.getData());
        }

    }

    public record Sugestao(TransacaoBanco transacao, SugestaoConciliacao tipo, List<Item> candidatos) {}

    public record Resultado(List<Sugestao> sugestoes, List<Item> semPar) {}

    /**
     * @param transacoes transações do banco ainda a revisar
     * @param itens lançamentos e movimentações da conta, perto dessas datas, que ainda não
     *              estão ligados a nenhuma transação
     */
    public static Resultado sugerir(List<TransacaoBanco> transacoes, List<Item> itens) {

        List<Sugestao> sugestoes = new ArrayList<>();
        Set<Item> usados = new HashSet<>();
        List<TransacaoBanco> semExato = new ArrayList<>();

        List<TransacaoBanco> restantes = new ArrayList<>();
        for (TransacaoBanco transacao : transacoes) {
            if (automatica(transacao)) {
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.IGNORAR, List.of()));
            } else {
                restantes.add(transacao);
            }
        }

        // Mesmo tipo e valor, perto na data.
        Map<Chave, List<Item>> sistemaPorChave = itens.stream()
                .collect(Collectors.groupingBy(i -> new Chave(i.tipo(), centavos(i.valor())), LinkedHashMap::new, Collectors.toList()));
        Map<Chave, List<TransacaoBanco>> bancoPorChave = restantes.stream()
                .collect(Collectors.groupingBy(t -> new Chave(t.getTipo(), centavos(t.getValor())), LinkedHashMap::new, Collectors.toList()));
        bancoPorChave.forEach((chave, doBanco) -> {
            List<TransacaoBanco> bancoOrdenado = doBanco.stream().sorted(Comparator.comparing(TransacaoBanco::getData)).toList();
            List<Item> sistemaOrdenado = sistemaPorChave.getOrDefault(chave, List.of()).stream()
                    .sorted(Comparator.comparing(Item::data)).toList();
            boolean emPares = bancoOrdenado.size() == sistemaOrdenado.size();
            for (int i = 0; emPares && i < bancoOrdenado.size(); i++) {
                emPares = distancia(bancoOrdenado.get(i), sistemaOrdenado.get(i)) <= DIAS_MESMO_VALOR;
            }
            if (emPares) {
                for (int i = 0; i < bancoOrdenado.size(); i++) {
                    sugestoes.add(new Sugestao(bancoOrdenado.get(i), SugestaoConciliacao.CONCILIAR, List.of(sistemaOrdenado.get(i))));
                }
                usados.addAll(sistemaOrdenado);
                return;
            }
            for (TransacaoBanco transacao : bancoOrdenado) {
                List<Item> candidatos = sistemaOrdenado.stream()
                        .filter(item -> distancia(transacao, item) <= DIAS_MESMO_VALOR)
                        .sorted(Comparator.comparingLong(item -> distancia(transacao, item))).toList();
                if (candidatos.isEmpty()) {
                    semExato.add(transacao);
                } else {
                    sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.ESCOLHER, candidatos));
                    usados.addAll(candidatos);
                }
            }
        });

        // Lançamento com valor próximo.
        semExato.sort(Comparator.comparing(TransacaoBanco::getData));
        for (TransacaoBanco transacao : semExato) {
            Optional<Item> proximo = itens.stream()
                    .filter(item -> item.lancamento() != null && !usados.contains(item) && item.tipo() == transacao.getTipo())
                    .filter(item -> perto(transacao, item))
                    .min(Comparator.<Item, BigDecimal>comparing(item -> item.valor().subtract(transacao.getValor()).abs())
                            .thenComparingLong(item -> distancia(transacao, item)));
            if (proximo.isPresent()) {
                usados.add(proximo.get());
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.VALOR_DIFERENTE, List.of(proximo.get())));
            } else {
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.CRIAR, List.of()));
            }
        }

        // Como no extrato: mais recente primeiro.
        sugestoes.sort(Comparator.comparing((Sugestao s) -> s.transacao().getData()).reversed()
                .thenComparing(s -> s.transacao().getId(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<Item> semPar = itens.stream().filter(item -> !usados.contains(item)).toList();
        return new Resultado(sugestoes, semPar);

    }

    /** Aplicação e resgate da aplicação automática ("APL APLIC AUT MAIS", "RES APLIC AUT MAIS"); o rendimento, não. */
    public static boolean automatica(TransacaoBanco transacao) {
        String descricao = transacao.getDescricao().toUpperCase(Locale.ROOT);
        return descricao.contains("APLIC AUT") && !descricao.contains("REND PAGO");
    }

    private record Chave(TipoLancamento tipo, BigDecimal valor) {}

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean perto(TransacaoBanco transacao, Item item) {
        BigDecimal diferenca = item.valor().subtract(transacao.getValor()).abs();
        BigDecimal limite = Conciliador.TOLERANCIA_REAIS.max(transacao.getValor().multiply(Conciliador.TOLERANCIA_PERCENTUAL));
        return diferenca.compareTo(limite) <= 0 && distancia(transacao, item) <= DIAS;
    }

    private static long distancia(TransacaoBanco transacao, Item item) {
        return Math.abs(ChronoUnit.DAYS.between(transacao.getData(), item.data()));
    }

}
