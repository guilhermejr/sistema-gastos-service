package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.mapper.MovimentacaoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.PagamentoFaturaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoResumidoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResumidaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.StatusFatura;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Uma fatura não é tabela: é o conjunto dos lançamentos de um cartão cujo vencimento
 * cai num mês, mais os pagamentos feitos para ele.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class FaturaService {

    private final LancamentoRepository lancamentoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final RecorrenciaService recorrenciaService;
    private final CartaoService cartaoService;
    private final ContaService contaService;
    private final LancamentoMapper lancamentoMapper;
    private final MovimentacaoMapper movimentacaoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final Clock clock;

    /** A fatura que recebe uma compra feita hoje. */
    @Transactional
    public FaturaResponse atual(Long cartaoId) {

        Cartao cartao = cartaoService.cartaoDoUsuario(cartaoId);
        return retornar(cartao, mesDaFaturaAtual(cartao));

    }

    @Transactional
    public FaturaResponse retornar(Long cartaoId, Integer ano, Integer mes) {

        Cartao cartao = cartaoService.cartaoDoUsuario(cartaoId);
        return retornar(cartao, converteStringUtil.toYearMonth(ano, mes));

    }

    /**
     * Paga o que está pendente na fatura: debita a conta (a informada ou a padrão do
     * cartão) e marca os lançamentos como realizados, ligados a este pagamento. Apagar o
     * pagamento (DELETE /movimentacoes/{id}) desfaz as duas coisas.
     */
    @Transactional
    public FaturaResponse pagar(Long cartaoId, Integer ano, Integer mes, PagamentoFaturaRequest pagamentoFaturaRequest) {

        Cartao cartao = cartaoService.cartaoDoUsuario(cartaoId);
        YearMonth mesFatura = converteStringUtil.toYearMonth(ano, mes);
        LocalDate vencimento = CalendarioUtil.vencimentoNoMes(mesFatura, cartao.getDiaVencimento());

        Conta conta = pagamentoFaturaRequest.getContaId() == null
                ? cartao.getConta()
                : contaService.contaDoUsuario(pagamentoFaturaRequest.getContaId());
        if (!conta.getAtivo()) {
            throw new ExceptionDefault("A conta " + conta.getNome() + " está desativada. Escolha outra para pagar.");
        }

        LocalDate data = pagamentoFaturaRequest.getData() == null
                ? LocalDate.now(clock)
                : converteStringUtil.toLocalDate(pagamentoFaturaRequest.getData());

        List<Lancamento> pendentes = lancamentos(cartao, mesFatura).stream().filter(l -> !l.getRealizado()).toList();
        BigDecimal valor = liquido(pendentes);
        if (valor.signum() <= 0) {
            throw new ExceptionDefault("Não há valor pendente nesta fatura.");
        }

        Movimentacao pagamento = new Movimentacao();
        pagamento.setTipo(TipoMovimentacao.PAGAMENTO_FATURA);
        pagamento.setDescricao("Fatura " + cartao.getNome() + " " + mesFatura.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        pagamento.setValor(valor);
        pagamento.setData(data);
        pagamento.setContaOrigem(conta);
        pagamento.setCartao(cartao);
        pagamento.setFatura(vencimento);
        pagamento.setUsuario(cartao.getUsuario());
        Movimentacao pagamentoSave = movimentacaoRepository.save(pagamento);

        pendentes.forEach(l -> {
            l.setRealizado(true);
            l.setPagamento(pagamentoSave);
        });
        lancamentoRepository.saveAll(pendentes);

        log.info("Fatura {} do cartão {} paga: {}", mesFatura, cartaoId, valor);
        return retornar(cartao, mesFatura);

    }

    /** Fatura atual do cartão, sem a lista de lançamentos. */
    public FaturaResumidaResponse resumoAtual(Cartao cartao) {

        YearMonth mes = mesDaFaturaAtual(cartao);
        return resumo(cartao, mes, lancamentos(cartao, mes));

    }

    /** Resumo da fatura do mês a partir dos lançamentos dela, já carregados. */
    public FaturaResumidaResponse resumo(Cartao cartao, YearMonth mes, List<Lancamento> lancamentos) {

        LocalDate vencimento = CalendarioUtil.vencimentoNoMes(mes, cartao.getDiaVencimento());
        LocalDate fechamento = CalendarioUtil.fechamento(vencimento, cartao.getDiasFechamento());
        BigDecimal pendente = liquido(lancamentos.stream().filter(l -> !l.getRealizado()).toList());

        return FaturaResumidaResponse.builder()
                .cartaoId(cartao.getId())
                .cartaoNome(cartao.getNome())
                .vencimento(vencimento)
                .fechamento(fechamento)
                .status(status(vencimento, fechamento, pendente))
                .total(liquido(lancamentos))
                .pendente(pendente)
                .build();

    }

    public YearMonth mesDaFaturaAtual(Cartao cartao) {

        return YearMonth.from(CalendarioUtil.vencimentoDaCompra(LocalDate.now(clock),
                cartao.getDiaVencimento(), cartao.getDiasFechamento()));

    }

    /** Despesas menos receitas (estornos, cashback). */
    public static BigDecimal liquido(List<Lancamento> lancamentos) {

        return lancamentos.stream()
                .map(l -> l.getTipo() == TipoLancamento.D ? l.getValor() : l.getValor().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

    }

    private FaturaResponse retornar(Cartao cartao, YearMonth mes) {

        recorrenciaService.gerarAte(cartao.getUsuario(), mes.atEndOfMonth());

        List<Lancamento> lancamentos = lancamentos(cartao, mes);
        List<Movimentacao> pagamentos = movimentacaoRepository
                .findAllByCartaoAndFaturaBetweenOrderByDataAscIdAsc(cartao, mes.atDay(1), mes.atEndOfMonth());

        LocalDate vencimento = CalendarioUtil.vencimentoNoMes(mes, cartao.getDiaVencimento());
        LocalDate fechamento = CalendarioUtil.fechamento(vencimento, cartao.getDiasFechamento());
        BigDecimal total = liquido(lancamentos);
        BigDecimal pendente = liquido(lancamentos.stream().filter(l -> !l.getRealizado()).toList());

        return FaturaResponse.builder()
                .cartao(new CartaoResumidoResponse(cartao.getId(), cartao.getNome()))
                .vencimento(vencimento)
                .fechamento(fechamento)
                .status(status(vencimento, fechamento, pendente))
                .total(total)
                .pago(total.subtract(pendente))
                .pendente(pendente)
                .lancamentos(lancamentoMapper.mapList(lancamentos))
                .pagamentos(movimentacaoMapper.mapList(pagamentos))
                .build();

    }

    private List<Lancamento> lancamentos(Cartao cartao, YearMonth mes) {
        return lancamentoRepository.findAllByCartaoAndFaturaBetweenOrderByDataAscIdAsc(cartao, mes.atDay(1), mes.atEndOfMonth());
    }

    private StatusFatura status(LocalDate vencimento, LocalDate fechamento, BigDecimal pendente) {

        LocalDate hoje = LocalDate.now(clock);
        if (hoje.isBefore(fechamento)) {
            return StatusFatura.ABERTA;
        }
        if (pendente.signum() <= 0) {
            return StatusFatura.PAGA;
        }
        return hoje.isAfter(vencimento) ? StatusFatura.VENCIDA : StatusFatura.FECHADA;

    }

}
