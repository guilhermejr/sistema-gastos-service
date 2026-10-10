package net.guilhermejr.sistema.gastosservice.util;

import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
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
 * Sugere, para cada transação do banco numa fatura, o lançamento do sistema com que ela
 * casa. Só sugere: quem liga os dois é o usuário, na revisão.
 *
 * <p>Ordem das tentativas:
 * <ol>
 *   <li>Pagamento da fatura anterior ("PAGAMENTO …" com valor negativo) → ignorar.</li>
 *   <li>Mesmo tipo, mesmo valor ao centavo e o mesmo número de parcelas <b>restantes</b>
 *   (à vista com à vista). Restantes, e não o número da parcela, porque o usuário lança
 *   só o que falta de uma compra antiga: a parcela 5/11 do banco é a 1/7 do sistema. A
 *   última parcela conta como à vista — quando falta uma só, ela é lançada sem parcelas
 *   (a 10/10 do banco é uma compra à vista no sistema).
 *   Quando há tantas transações quanto lançamentos com a mesma chave, casam em ordem de
 *   data; quando os números diferem, o usuário escolhe entre os candidatos.</li>
 *   <li>À vista, sem par exato: o lançamento à vista mais próximo em valor (até
 *   {@link #TOLERANCIA_REAIS} ou {@link #TOLERANCIA_PERCENTUAL}) e em data (até
 *   {@link #DIAS} dias) → valor diferente, para corrigir ou manter.</li>
 *   <li>O resto → criar lançamento.</li>
 * </ol>
 * Lançamentos que não entraram em nenhuma sugestão voltam em {@code semPar}.
 */
public final class Conciliador {

    /**
     * Diferenças reais vistas: Disney 69,90 × 66,90 e Claude 115,94 × 120,87, ambas ~4%.
     * Com 10%, uma compra de 68,40 casaria com um almoço de 61,80 do mesmo dia.
     */
    static final BigDecimal TOLERANCIA_REAIS = new BigDecimal("1.00");
    static final BigDecimal TOLERANCIA_PERCENTUAL = new BigDecimal("0.05");
    static final int DIAS = 5;

    private Conciliador() {
    }

    public record Sugestao(TransacaoBanco transacao, SugestaoConciliacao tipo, List<Lancamento> candidatos) {}

    public record Resultado(List<Sugestao> sugestoes, List<Lancamento> semPar) {}

    /**
     * @param transacoes transações do banco ainda a revisar, de uma fatura
     * @param lancamentos lançamentos da mesma fatura que ainda não estão ligados a nenhuma transação
     */
    public static Resultado sugerir(List<TransacaoBanco> transacoes, List<Lancamento> lancamentos) {

        List<Sugestao> sugestoes = new ArrayList<>();
        Set<Lancamento> usados = new HashSet<>();
        List<TransacaoBanco> semExato = new ArrayList<>();

        List<TransacaoBanco> compras = new ArrayList<>();
        for (TransacaoBanco transacao : transacoes) {
            if (pagamento(transacao)) {
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.IGNORAR, List.of()));
            } else {
                compras.add(transacao);
            }
        }

        // Mesmo valor e mesmas parcelas restantes.
        Map<Chave, List<Lancamento>> sistemaPorChave = lancamentos.stream()
                .collect(Collectors.groupingBy(Conciliador::chave, LinkedHashMap::new, Collectors.toList()));
        Map<Chave, List<TransacaoBanco>> bancoPorChave = compras.stream()
                .collect(Collectors.groupingBy(Conciliador::chave, LinkedHashMap::new, Collectors.toList()));
        bancoPorChave.forEach((chave, doBanco) -> {
            List<Lancamento> doSistema = sistemaPorChave.getOrDefault(chave, List.of());
            if (doSistema.isEmpty()) {
                semExato.addAll(doBanco);
                return;
            }
            List<TransacaoBanco> bancoOrdenado = doBanco.stream().sorted(Comparator.comparing(Conciliador::dataDaCompra)).toList();
            List<Lancamento> sistemaOrdenado = doSistema.stream().sorted(Comparator.comparing(Lancamento::getData)).toList();
            if (bancoOrdenado.size() == sistemaOrdenado.size()) {
                for (int i = 0; i < bancoOrdenado.size(); i++) {
                    sugestoes.add(new Sugestao(bancoOrdenado.get(i), SugestaoConciliacao.CONCILIAR, List.of(sistemaOrdenado.get(i))));
                }
            } else {
                for (TransacaoBanco transacao : bancoOrdenado) {
                    List<Lancamento> candidatos = sistemaOrdenado.stream()
                            .sorted(Comparator.comparingLong(l -> distancia(transacao, l))).toList();
                    sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.ESCOLHER, candidatos));
                }
            }
            usados.addAll(sistemaOrdenado);
        });

        // À vista com valor próximo.
        semExato.sort(Comparator.comparing(Conciliador::dataDaCompra));
        for (TransacaoBanco transacao : semExato) {
            Optional<Lancamento> proximo = restantes(transacao.getParcela(), transacao.getTotalParcelas()) != null ? Optional.empty() : lancamentos.stream()
                    .filter(l -> !usados.contains(l) && l.getTipo() == transacao.getTipo() && restantes(l) == null)
                    .filter(l -> perto(transacao, l))
                    .min(Comparator.<Lancamento, BigDecimal>comparing(l -> l.getValor().subtract(transacao.getValor()).abs())
                            .thenComparingLong(l -> distancia(transacao, l)));
            if (proximo.isPresent()) {
                usados.add(proximo.get());
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.VALOR_DIFERENTE, List.of(proximo.get())));
            } else {
                sugestoes.add(new Sugestao(transacao, SugestaoConciliacao.CRIAR, List.of()));
            }
        }

        // Na ordem da fatura: mais recente primeiro.
        sugestoes.sort(Comparator.comparing((Sugestao s) -> s.transacao().getData()).reversed()
                .thenComparing(s -> s.transacao().getId(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<Lancamento> semPar = lancamentos.stream().filter(l -> !usados.contains(l)).toList();
        return new Resultado(sugestoes, semPar);

    }

    /** O pagamento da fatura anterior entra como crédito "PAGAMENTO COM SALDO" ou "Pagamento recebido". */
    public static boolean pagamento(TransacaoBanco transacao) {
        return transacao.getTipo() == TipoLancamento.R
                && transacao.getDescricao().toUpperCase(Locale.ROOT).startsWith("PAGAMENTO");
    }

    private record Chave(TipoLancamento tipo, BigDecimal valor, Integer restantes) {}

    private static Chave chave(Lancamento lancamento) {
        return new Chave(lancamento.getTipo(), centavos(lancamento.getValor()), restantes(lancamento));
    }

    private static Chave chave(TransacaoBanco transacao) {
        return new Chave(transacao.getTipo(), centavos(transacao.getValor()), restantes(transacao.getParcela(), transacao.getTotalParcelas()));
    }

    private static Integer restantes(Lancamento lancamento) {
        return restantes(lancamento.getParcela(), lancamento.getTotalParcelas());
    }

    /** Parcelas que ainda faltam depois desta; null para compra à vista e para a última parcela. */
    private static Integer restantes(Integer parcela, Integer total) {
        if (parcela == null || total == null || total - parcela <= 0) {
            return null;
        }
        return total - parcela;
    }

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean perto(TransacaoBanco transacao, Lancamento lancamento) {
        BigDecimal diferenca = lancamento.getValor().subtract(transacao.getValor()).abs();
        BigDecimal limite = TOLERANCIA_REAIS.max(transacao.getValor().multiply(TOLERANCIA_PERCENTUAL));
        return diferenca.compareTo(limite) <= 0 && distancia(transacao, lancamento) <= DIAS;
    }

    /** Parcelas do banco vêm com a data da cobrança; a da compra é a que o usuário lança. */
    private static LocalDate dataDaCompra(TransacaoBanco transacao) {
        return transacao.getDataCompra() != null && transacao.getTotalParcelas() == null ? transacao.getDataCompra() : transacao.getData();
    }

    private static long distancia(TransacaoBanco transacao, Lancamento lancamento) {
        return Math.abs(ChronoUnit.DAYS.between(dataDaCompra(transacao), lancamento.getData()));
    }

}
