package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.mapper.TransacaoBancoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConfirmarConciliacaoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CriarDoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.FaturaDestinoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConciliacaoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LinhaConciliacaoResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.Escopo;
import net.guilhermejr.sistema.gastosservice.domain.enums.Repeticao;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.Conciliador;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Conciliação de uma fatura do cartão com o banco: cada transação do banco com o que
 * fazer com ela. As sugestões são calculadas a cada consulta, nada é gravado aqui.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class ConciliacaoService {

    private final PluggyClient pluggyClient;
    private final CartaoService cartaoService;
    private final RecorrenciaService recorrenciaService;
    private final LancamentoRepository lancamentoRepository;
    private final TransacaoBancoRepository transacaoBancoRepository;
    private final LancamentoMapper lancamentoMapper;
    private final TransacaoBancoMapper transacaoBancoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;
    private final LancamentoService lancamentoService;

    private static final DateTimeFormatter DATA_BRASIL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional
    public ConciliacaoResponse conciliacao(Long cartaoId, Integer ano, Integer mes) {

        return conciliacao(cartaoLiberado(cartaoId), converteStringUtil.toYearMonth(ano, mes));

    }

    /**
     * Liga a transação do banco a um lançamento da mesma fatura. Com {@code corrigirValor},
     * o lançamento passa a ter o valor do banco — só ele, não as próximas ocorrências.
     */
    @Transactional
    public ConciliacaoResponse conciliar(Long cartaoId, Long transacaoId, ConciliarRequest conciliarRequest) {

        Cartao cartao = cartaoLiberado(cartaoId);
        TransacaoBanco transacao = transacaoARevisar(cartao, transacaoId);
        Lancamento lancamento = lancamentoLivre(cartao, transacao, conciliarRequest.getLancamentoId());

        if (Boolean.TRUE.equals(conciliarRequest.getCorrigirValor()) && lancamento.getValor().compareTo(transacao.getValor()) != 0) {
            if (lancamento.getParcelamento() != null) {
                throw new ExceptionDefault("O valor de uma parcela não é corrigido por aqui. Edite a compra parcelada.");
            }
            lancamentoService.atualizar(lancamento.getId(), requisicao(lancamento, cartao, transacao.getValor()), Escopo.UNICO);
            log.info("Lançamento {} corrigido para o valor do banco: {}", lancamento.getId(), transacao.getValor());
        }
        ligar(transacao, lancamento, SituacaoTransacaoBanco.CONCILIADA);
        return conciliacao(cartao, YearMonth.from(transacao.getFatura()));

    }

    /** Confirma várias de uma vez (o que bateu e o que é para ignorar). Tudo ou nada. */
    @Transactional
    public ConciliacaoResponse confirmar(Long cartaoId, Integer ano, Integer mes, ConfirmarConciliacaoRequest confirmarConciliacaoRequest) {

        Cartao cartao = cartaoLiberado(cartaoId);
        for (ConfirmarConciliacaoRequest.Item item : confirmarConciliacaoRequest.getItens()) {
            TransacaoBanco transacao = transacaoARevisar(cartao, item.getTransacaoId());
            if (item.getLancamentoId() == null) {
                ignorar(transacao);
            } else {
                ligar(transacao, lancamentoLivre(cartao, transacao, item.getLancamentoId()), SituacaoTransacaoBanco.CONCILIADA);
            }
        }
        log.info("Cartão {}: {} transações do banco confirmadas", cartaoId, confirmarConciliacaoRequest.getItens().size());
        return conciliacao(cartao, converteStringUtil.toYearMonth(ano, mes));

    }

    /**
     * Cria o lançamento a partir da transação, na fatura em que o banco a pôs. Parcela do
     * banco vira compra parcelada com as parcelas que faltam, contando esta — como o
     * usuário lança uma compra antiga: a 5/10 vira 6 parcelas. A última vira compra à vista.
     */
    @Transactional
    public ConciliacaoResponse criar(Long cartaoId, Long transacaoId, CriarDoBancoRequest criarDoBancoRequest) {

        Cartao cartao = cartaoLiberado(cartaoId);
        TransacaoBanco transacao = transacaoARevisar(cartao, transacaoId);

        int parcelas = transacao.getTotalParcelas() == null ? 1 : transacao.getTotalParcelas() - transacao.getParcela() + 1;
        YearMonth mes = YearMonth.from(transacao.getFatura());
        LancamentoRequest lancamentoRequest = LancamentoRequest.builder()
                .tipo(transacao.getTipo())
                .descricao(criarDoBancoRequest.getDescricao().trim())
                .valor(moeda(transacao.getValor().multiply(BigDecimal.valueOf(parcelas))))
                .data(transacao.getData().format(DATA_BRASIL))
                .categoriaId(criarDoBancoRequest.getCategoriaId())
                .cartaoId(cartao.getId())
                .repeticao(parcelas > 1 ? Repeticao.PARCELADA : Repeticao.UNICA)
                .parcelas(parcelas > 1 ? parcelas : null)
                .fatura(new FaturaDestinoRequest(mes.getYear(), mes.getMonthValue()))
                .build();

        // A transação fica ligada à parcela que cai na fatura dela (a primeira).
        List<LancamentoResponse> criados = lancamentoService.inserir(lancamentoRequest);
        Long id = criados.stream().filter(c -> YearMonth.from(c.getFatura()).equals(mes)).findFirst()
                .orElse(criados.get(0)).getId();
        ligar(transacao, lancamentoService.lancamentoDoUsuario(id), SituacaoTransacaoBanco.IMPORTADA);
        return conciliacao(cartao, mes);

    }

    @Transactional
    public ConciliacaoResponse ignorar(Long cartaoId, Long transacaoId) {

        Cartao cartao = cartaoLiberado(cartaoId);
        TransacaoBanco transacao = transacaoARevisar(cartao, transacaoId);
        ignorar(transacao);
        return conciliacao(cartao, YearMonth.from(transacao.getFatura()));

    }

    /** Volta a transação para revisar. O lançamento criado a partir dela continua existindo. */
    @Transactional
    public ConciliacaoResponse desfazer(Long cartaoId, Long transacaoId) {

        Cartao cartao = cartaoLiberado(cartaoId);
        TransacaoBanco transacao = transacaoDoCartao(cartao, transacaoId);
        if (aRevisar(transacao)) {
            throw new ExceptionDefault("Esta transação ainda não foi revisada.");
        }
        transacao.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
        transacao.setLancamento(null);
        transacaoBancoRepository.save(transacao);
        return conciliacao(cartao, YearMonth.from(transacao.getFatura()));

    }

    private ConciliacaoResponse conciliacao(Cartao cartao, YearMonth mesFatura) {

        // As despesas fixas do mês precisam existir para casar com o banco, como na fatura.
        recorrenciaService.gerarAte(cartao.getUsuario(), mesFatura.atEndOfMonth());
        List<Lancamento> lancamentos = lancamentoRepository.findAllByCartaoAndFaturaBetweenOrderByDataAscIdAsc(
                cartao, mesFatura.atDay(1), mesFatura.atEndOfMonth());
        List<TransacaoBanco> transacoes = transacaoBancoRepository.findAllByCartaoAndFaturaBetweenOrderByDataDescIdDesc(
                cartao, mesFatura.atDay(1), mesFatura.atEndOfMonth());

        // Conciliada ou importada cujo lançamento foi apagado volta a ser revisada.
        List<TransacaoBanco> aRevisar = transacoes.stream().filter(ConciliacaoService::aRevisar).toList();
        Set<Long> ligados = transacaoBancoRepository.lancamentosLigados(cartao);
        List<Lancamento> livres = lancamentos.stream().filter(l -> !ligados.contains(l.getId())).toList();

        Conciliador.Resultado resultado = Conciliador.sugerir(aRevisar, livres);

        List<LinhaConciliacaoResponse> linhas = new ArrayList<>();
        resultado.sugestoes().forEach(s -> linhas.add(new LinhaConciliacaoResponse(transacaoBancoMapper.mapObject(s.transacao()),
                s.tipo(), lancamentoMapper.mapList(s.candidatos()), null,
                s.tipo() == SugestaoConciliacao.CRIAR ? categoriaSugerida(cartao, s.transacao()) : null)));
        transacoes.stream().filter(t -> !aRevisar(t)).forEach(t -> linhas.add(new LinhaConciliacaoResponse(transacaoBancoMapper.mapObject(t),
                null, List.of(), t.getLancamento() == null ? null : lancamentoMapper.mapObject(t.getLancamento()), null)));

        BigDecimal totalBanco = transacoes.stream()
                .filter(t -> !Conciliador.pagamento(t) && t.getSituacao() != SituacaoTransacaoBanco.IGNORADA)
                .map(ConciliacaoService::liquido).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ConciliacaoResponse(linhas, lancamentoMapper.mapList(resultado.semPar()), totalBanco, FaturaService.liquido(lancamentos));

    }

    private Long categoriaSugerida(Cartao cartao, TransacaoBanco transacao) {

        if (transacao.getCategoriaBanco() == null) {
            return null;
        }
        return transacaoBancoRepository.findFirstByUsuarioAndCategoriaBancoAndLancamentoNotNullOrderByAtualizadoDesc(
                        cartao.getUsuario(), transacao.getCategoriaBanco())
                .map(t -> t.getLancamento().getCategoria())
                .filter(categoria -> categoria.getAtivo() && categoria.getTipo() == transacao.getTipo())
                .map(Categoria::getId).orElse(null);

    }

    private void ligar(TransacaoBanco transacao, Lancamento lancamento, SituacaoTransacaoBanco situacao) {

        transacao.setLancamento(lancamento);
        transacao.setSituacao(situacao);
        transacaoBancoRepository.save(transacao);

    }

    private void ignorar(TransacaoBanco transacao) {

        transacao.setLancamento(null);
        transacao.setSituacao(SituacaoTransacaoBanco.IGNORADA);
        transacaoBancoRepository.save(transacao);

    }

    /** O lançamento precisa ser do cartão, da mesma fatura, do mesmo tipo e ainda sem transação ligada. */
    private Lancamento lancamentoLivre(Cartao cartao, TransacaoBanco transacao, Long lancamentoId) {

        Lancamento lancamento = lancamentoService.lancamentoDoUsuario(lancamentoId);
        if (lancamento.getCartao() == null || !lancamento.getCartao().getId().equals(cartao.getId())
                || !YearMonth.from(lancamento.getFatura()).equals(YearMonth.from(transacao.getFatura()))) {
            throw new ExceptionDefault("O lançamento não é desta fatura do cartão.");
        }
        if (lancamento.getTipo() != transacao.getTipo()) {
            throw new ExceptionDefault("O lançamento e a transação do banco não são do mesmo tipo.");
        }
        if (transacaoBancoRepository.existsByLancamento(lancamento)) {
            throw new ExceptionDefault("O lançamento já está ligado a outra transação do banco.");
        }
        return lancamento;

    }

    private TransacaoBanco transacaoARevisar(Cartao cartao, Long transacaoId) {

        TransacaoBanco transacao = transacaoDoCartao(cartao, transacaoId);
        if (!aRevisar(transacao)) {
            throw new ExceptionDefault("Esta transação já foi revisada.");
        }
        return transacao;

    }

    private TransacaoBanco transacaoDoCartao(Cartao cartao, Long transacaoId) {

        return transacaoBancoRepository.findByIdAndCartao(transacaoId, cartao)
                .orElseThrow(() -> new ExceptionNotFound("Transação do banco não encontrada: " + transacaoId));

    }

    private Cartao cartaoLiberado(Long cartaoId) {

        if (!pluggyClient.liberadoPara(authenticationCurrentUserService.getCurrentUser().getId())) {
            throw new ExceptionDefault("A integração com o banco não está disponível.");
        }
        return cartaoService.cartaoDoUsuario(cartaoId);

    }

    /** O mesmo lançamento, com outro valor — para LancamentoService.atualizar. */
    private static LancamentoRequest requisicao(Lancamento lancamento, Cartao cartao, BigDecimal valor) {

        return LancamentoRequest.builder()
                .tipo(lancamento.getTipo())
                .descricao(lancamento.getDescricao())
                .valor(moeda(valor))
                .data(lancamento.getData().format(DATA_BRASIL))
                .categoriaId(lancamento.getCategoria().getId())
                .cartaoId(cartao.getId())
                .build();

    }

    /** 1234.5 → "1234,50", o formato que os requests de lançamento recebem. */
    private static String moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static boolean aRevisar(TransacaoBanco transacao) {
        return transacao.getSituacao() == SituacaoTransacaoBanco.A_REVISAR
                || (transacao.getSituacao() != SituacaoTransacaoBanco.IGNORADA && transacao.getLancamento() == null);
    }

    private static BigDecimal liquido(TransacaoBanco transacao) {
        return transacao.getTipo() == TipoLancamento.D ? transacao.getValor() : transacao.getValor().negate();
    }

}
