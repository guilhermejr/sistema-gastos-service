package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.MovimentacaoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.MovimentacaoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.MovimentacaoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ContaService contaService;
    private final MovimentacaoMapper movimentacaoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    /** Movimentações de uma conta num mês (depósitos, saques, transferências e faturas pagas). */
    public List<MovimentacaoResponse> retornarDaConta(Long contaId, Integer ano, Integer mes) {

        Conta conta = contaService.contaDoUsuario(contaId);
        YearMonth periodo = converteStringUtil.toYearMonth(ano, mes);
        return movimentacaoMapper.mapList(movimentacaoRepository
                .findDaContaNoPeriodo(conta.getUsuario(), conta, periodo.atDay(1), periodo.atEndOfMonth()));

    }

    @Transactional
    public MovimentacaoResponse inserir(MovimentacaoRequest movimentacaoRequest) {

        TipoMovimentacao tipo = movimentacaoRequest.getTipo();
        boolean temOrigem = movimentacaoRequest.getContaOrigemId() != null;
        boolean temDestino = movimentacaoRequest.getContaDestinoId() != null;

        switch (tipo) {
            case DEPOSITO -> exige(!temOrigem && temDestino, "Depósito precisa só da conta de destino.");
            case SAQUE -> exige(temOrigem && !temDestino, "Saque precisa só da conta de origem.");
            case TRANSFERENCIA -> {
                exige(temOrigem && temDestino, "Transferência precisa da conta de origem e da de destino.");
                exige(!movimentacaoRequest.getContaOrigemId().equals(movimentacaoRequest.getContaDestinoId()),
                        "A conta de origem e a de destino precisam ser diferentes.");
            }
            case PAGAMENTO_FATURA -> throw new ExceptionDefault("Fatura se paga pelo cartão, não por aqui.");
        }

        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setTipo(tipo);
        movimentacao.setDescricao(movimentacaoRequest.getDescricao() == null || movimentacaoRequest.getDescricao().isBlank()
                ? null : movimentacaoRequest.getDescricao().trim());
        movimentacao.setValor(converteStringUtil.toValorPositivo(movimentacaoRequest.getValor()));
        movimentacao.setData(converteStringUtil.toLocalDate(movimentacaoRequest.getData()));
        if (temOrigem) {
            movimentacao.setContaOrigem(contaService.contaAtivaDoUsuario(movimentacaoRequest.getContaOrigemId()));
        }
        if (temDestino) {
            movimentacao.setContaDestino(contaService.contaAtivaDoUsuario(movimentacaoRequest.getContaDestinoId()));
        }
        movimentacao.setUsuario(usuario());

        return movimentacaoMapper.mapObject(movimentacaoRepository.save(movimentacao));

    }

    /**
     * Apaga a movimentação. Se for pagamento de fatura, é um estorno: os lançamentos que
     * ele quitou voltam a ficar pendentes na fatura.
     */
    @Transactional
    public void apagar(Long id) {

        Movimentacao movimentacao = movimentacaoRepository.findByIdAndUsuario(id, usuario())
                .orElseThrow(() -> new ExceptionNotFound("Movimentação não encontrada: " + id));

        if (movimentacao.getTipo() == TipoMovimentacao.PAGAMENTO_FATURA) {
            List<Lancamento> quitados = lancamentoRepository.findAllByPagamento(movimentacao);
            quitados.forEach(l -> {
                l.setRealizado(false);
                l.setPagamento(null);
            });
            lancamentoRepository.saveAllAndFlush(quitados);
            log.info("Pagamento de fatura {} estornado; {} lançamentos voltaram a pendentes", id, quitados.size());
        }

        movimentacaoRepository.delete(movimentacao);

    }

    private void exige(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new ExceptionDefault(mensagem);
        }
    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
