package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Recorrencia;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Despesas e receitas fixas. As ocorrências não são criadas todas de uma vez — a série
 * não tem fim. Cada consulta que olha para um período chama {@link #gerarAte} antes,
 * e só então as ocorrências que faltam até aquela data viram lançamentos.
 *
 * <p>Por isso apagar uma ocorrência é definitivo: a geração anda só para a frente,
 * a partir de {@code geradoAte}, e não recria o que ficou para trás.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class RecorrenciaService {

    private final RecorrenciaRepository recorrenciaRepository;
    private final LancamentoRepository lancamentoRepository;

    /** Cria a série a partir do primeiro lançamento, que já é a primeira ocorrência. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Recorrencia criar(Lancamento primeiro) {

        Recorrencia recorrencia = new Recorrencia();
        copiarParaMolde(primeiro, recorrencia);
        recorrencia.setGeradoAte(primeiro.getData());
        recorrencia.setAtivo(true);
        recorrencia.setUsuario(primeiro.getUsuario());
        return recorrenciaRepository.save(recorrencia);

    }

    /** Cria as ocorrências que faltam, de todas as séries ativas do usuário, até a data informada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void gerarAte(UUID usuario, LocalDate ate) {

        List<Lancamento> novos = new ArrayList<>();

        for (Recorrencia recorrencia : recorrenciaRepository.findParaGerar(usuario, ate)) {
            LocalDate proxima = CalendarioUtil.mesesDepois(recorrencia.getGeradoAte(), recorrencia.getDia(), 1);
            while (!proxima.isAfter(ate)) {
                novos.add(ocorrencia(recorrencia, proxima));
                recorrencia.setGeradoAte(proxima);
                proxima = CalendarioUtil.mesesDepois(proxima, recorrencia.getDia(), 1);
            }
        }

        if (!novos.isEmpty()) {
            lancamentoRepository.saveAll(novos);
            log.info("Geradas {} ocorrências de lançamentos fixos até {}", novos.size(), ate);
        }

    }

    /**
     * Encerra a série: nada mais é gerado, e as ocorrências ainda não realizadas a partir
     * da data informada são apagadas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void encerrar(Recorrencia recorrencia, LocalDate aPartirDe) {

        List<Lancamento> futuras = lancamentoRepository.findAllByRecorrenciaAndDataGreaterThanEqualAndRealizadoFalse(recorrencia, aPartirDe);
        lancamentoRepository.deleteAll(futuras);
        recorrencia.setAtivo(false);
        recorrenciaRepository.save(recorrencia);
        log.info("Recorrência {} encerrada a partir de {}; {} ocorrências apagadas", recorrencia.getId(), aPartirDe, futuras.size());

    }

    /** O molde passa a refletir o lançamento: as próximas ocorrências saem iguais a ele. */
    public void copiarParaMolde(Lancamento lancamento, Recorrencia recorrencia) {

        recorrencia.setTipo(lancamento.getTipo());
        recorrencia.setDescricao(lancamento.getDescricao());
        recorrencia.setValor(lancamento.getValor());
        recorrencia.setDia(lancamento.getData().getDayOfMonth());
        recorrencia.setCategoria(lancamento.getCategoria());
        recorrencia.setConta(lancamento.getConta());
        recorrencia.setCartao(lancamento.getCartao());

    }

    private Lancamento ocorrencia(Recorrencia recorrencia, LocalDate data) {

        Lancamento lancamento = new Lancamento();
        lancamento.setTipo(recorrencia.getTipo());
        lancamento.setDescricao(recorrencia.getDescricao());
        lancamento.setValor(recorrencia.getValor());
        lancamento.setData(data);
        lancamento.setCategoria(recorrencia.getCategoria());
        lancamento.setConta(recorrencia.getConta());
        lancamento.setCartao(recorrencia.getCartao());
        if (recorrencia.getCartao() != null) {
            lancamento.setFatura(CalendarioUtil.vencimentoDaCompra(data,
                    recorrencia.getCartao().getDiaVencimento(), recorrencia.getCartao().getDiasFechamento()));
        }
        lancamento.setRealizado(false);
        lancamento.setRecorrencia(recorrencia);
        lancamento.setUsuario(recorrencia.getUsuario());
        return lancamento;

    }

}
