package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.sistema.gastosservice.api.response.ContaResumidoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ExtratoContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LinhaExtratoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.Ciclo;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extrato de uma conta num período, com a mesma regra do {@link SaldoService}, só que por
 * data: lançamentos realizados na conta e movimentações (depósito, saque, transferência,
 * pagamento de fatura). Pendente não entra — não mexeu no saldo.
 *
 * <p>O saldo anterior é o saldo inicial mais tudo o que é de antes de {@code inicio}; cada
 * linha leva o saldo depois dela, e o da última é o saldo final. Como o saldo da conta
 * conta movimentações já registradas com data futura, o saldo final só bate com ele quando
 * {@code fim} passa da última data.
 */
@RequiredArgsConstructor
@Service
public class ExtratoService {

    private final ContaService contaService;
    private final ConfiguracaoService configuracaoService;
    private final LancamentoRepository lancamentoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final ConverteStringUtil converteStringUtil;
    private final Clock clock;

    /** Sem datas, o ciclo atual — o "mês" do dashboard e do relatório. */
    @Transactional(readOnly = true)
    public ExtratoContaResponse extrato(Long contaId, String inicioTexto, String fimTexto) {

        Conta conta = contaService.contaDoUsuario(contaId);
        LocalDate inicio;
        LocalDate fim;
        if (inicioTexto == null && fimTexto == null) {
            Ciclo ciclo = Ciclo.daData(LocalDate.now(clock), configuracaoService.diaInicioCiclo(conta.getUsuario()));
            inicio = ciclo.inicio();
            fim = ciclo.fim();
        } else {
            inicio = data(inicioTexto, "inicial");
            fim = data(fimTexto, "final");
        }
        if (inicio.isAfter(fim)) {
            throw new ExceptionDefault("A data inicial precisa ser igual ou anterior à final.");
        }

        BigDecimal saldoAnterior = conta.getSaldoInicial().add(movimentacaoRepository.somarDaContaAntesDe(conta, inicio));
        for (Object[] linha : lancamentoRepository.somarRealizadosDaContaAntesDe(conta, inicio)) {
            BigDecimal soma = (BigDecimal) linha[1];
            saldoAnterior = saldoAnterior.add(linha[0] == TipoLancamento.R ? soma : soma.negate());
        }

        List<Linha> itens = new ArrayList<>();
        lancamentoRepository.findAllByContaAndRealizadoTrueAndDataBetweenOrderByDataAscIdAsc(conta, inicio, fim)
                .forEach(l -> itens.add(Linha.de(l)));
        movimentacaoRepository.findDaContaNoPeriodo(conta.getUsuario(), conta, inicio, fim)
                .forEach(m -> itens.add(Linha.de(m, conta)));
        // No mesmo dia, na ordem em que foram registrados.
        itens.sort(Comparator.comparing(Linha::data)
                .thenComparing(Linha::criado, Comparator.nullsFirst(Comparator.naturalOrder())));

        BigDecimal saldo = saldoAnterior;
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        List<LinhaExtratoResponse> linhas = new ArrayList<>();
        for (Linha item : itens) {
            LinhaExtratoResponse resposta = item.resposta();
            if (resposta.getTipo() == TipoLancamento.R) {
                entradas = entradas.add(resposta.getValor());
                saldo = saldo.add(resposta.getValor());
            } else {
                saidas = saidas.add(resposta.getValor());
                saldo = saldo.subtract(resposta.getValor());
            }
            resposta.setSaldo(saldo);
            linhas.add(resposta);
        }

        return new ExtratoContaResponse(new ContaResumidoResponse(conta.getId(), conta.getNome()), inicio, fim,
                saldoAnterior, entradas, saidas, saldo, linhas);

    }

    private LocalDate data(String texto, String qual) {

        if (texto == null || texto.isBlank()) {
            throw new ExceptionDefault("Informe a data " + qual + ".");
        }
        try {
            return converteStringUtil.toLocalDate(texto);
        } catch (DateTimeParseException ex) {
            throw new ExceptionDefault("Data " + qual + " inválida: " + texto);
        }

    }

    /** Lançamento ou movimentação já vistos pela conta, com o que ordena o extrato. */
    private record Linha(LocalDate data, LocalDateTime criado, LinhaExtratoResponse resposta) {

        static Linha de(Lancamento l) {
            return new Linha(l.getData(), l.getCriado(), new LinhaExtratoResponse(l.getId(), null, null, l.getTipo(),
                    l.getDescricao(), l.getCategoria() == null ? null : l.getCategoria().getDescricao(),
                    l.getValor(), l.getData(), null));
        }

        static Linha de(Movimentacao m, Conta conta) {
            boolean saiu = m.getContaOrigem() != null && m.getContaOrigem().getId().equals(conta.getId());
            return new Linha(m.getData(), m.getCriado(), new LinhaExtratoResponse(null, m.getId(), m.getTipo(),
                    saiu ? TipoLancamento.D : TipoLancamento.R, ConciliacaoContaService.descricao(m), null,
                    m.getValor(), m.getData(), null));
        }

    }

}
