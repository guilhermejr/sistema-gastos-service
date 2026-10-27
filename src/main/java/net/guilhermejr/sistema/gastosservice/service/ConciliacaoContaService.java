package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.TransacaoBancoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConfirmarConciliacaoContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CriarDoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.PagarFaturaContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.RendimentoContaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConciliacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResumidaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ItemContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LinhaConciliacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.Escopo;
import net.guilhermejr.sistema.gastosservice.domain.enums.Repeticao;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import net.guilhermejr.sistema.gastosservice.util.ConciliadorConta;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Conciliação de um mês da conta corrente com o extrato do banco. Conciliar com um
 * lançamento o dá como realizado na data do banco, se a transação já aconteceu; se ainda
 * está pendente (um pagamento agendado), só liga, e a sincronização o realiza quando o
 * banco efetivar. Com uma movimentação, só liga (ela já conta no saldo). As sugestões são
 * calculadas a cada consulta, nada é gravado aqui.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class ConciliacaoContaService {

    /**
     * Quantos dias antes e depois do mês os itens do sistema ainda servem de candidatos: a
     * janela do mesmo valor ({@link ConciliadorConta#DIAS_MESMO_VALOR}), senão um item perto
     * da virada do mês nem chega ao conciliador.
     */
    private static final int MARGEM_DIAS = ConciliadorConta.DIAS_MESMO_VALOR;
    private static final DateTimeFormatter DATA_BRASIL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PluggyClient pluggyClient;
    private final ContaService contaService;
    private final SaldoService saldoService;
    private final RecorrenciaService recorrenciaService;
    private final LancamentoService lancamentoService;
    private final FaturaService faturaService;
    private final CartaoService cartaoService;
    private final CartaoRepository cartaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final TransacaoBancoRepository transacaoBancoRepository;
    private final TransacaoBancoMapper transacaoBancoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;
    private final Clock clock;

    @Transactional
    public ConciliacaoContaResponse conciliacao(Long contaId, Integer ano, Integer mes) {

        return conciliacao(contaLigada(contaId), converteStringUtil.toYearMonth(ano, mes));

    }

    /**
     * Liga a transação a um lançamento ou a uma movimentação da conta. Com
     * {@code corrigirValor}, o lançamento passa a ter o valor do banco — só ele.
     */
    @Transactional
    public ConciliacaoContaResponse conciliar(Long contaId, Long transacaoId, ConciliarContaRequest conciliarContaRequest) {

        Conta conta = contaLigada(contaId);
        TransacaoBanco transacao = transacaoARevisar(conta, transacaoId);
        ligar(conta, transacao, conciliarContaRequest.getLancamentoId(), conciliarContaRequest.getMovimentacaoId(),
                Boolean.TRUE.equals(conciliarContaRequest.getCorrigirValor()));
        return conciliacao(conta, YearMonth.from(transacao.getData()));

    }

    /** Confirma várias de uma vez (o que bateu e o que é para ignorar). Tudo ou nada. */
    @Transactional
    public ConciliacaoContaResponse confirmar(Long contaId, Integer ano, Integer mes, ConfirmarConciliacaoContaRequest confirmarConciliacaoContaRequest) {

        Conta conta = contaLigada(contaId);
        for (ConfirmarConciliacaoContaRequest.Item item : confirmarConciliacaoContaRequest.getItens()) {
            TransacaoBanco transacao = transacaoARevisar(conta, item.getTransacaoId());
            if (item.getLancamentoId() == null && item.getMovimentacaoId() == null) {
                ignorar(transacao);
            } else {
                ligar(conta, transacao, item.getLancamentoId(), item.getMovimentacaoId(), false);
            }
        }
        log.info("Conta {}: {} transações do banco confirmadas", contaId, confirmarConciliacaoContaRequest.getItens().size());
        return conciliacao(conta, converteStringUtil.toYearMonth(ano, mes));

    }

    /** Cria um lançamento avulso com a data e o valor do banco; realizado se a transação já aconteceu. */
    @Transactional
    public ConciliacaoContaResponse criar(Long contaId, Long transacaoId, CriarDoBancoRequest criarDoBancoRequest) {

        Conta conta = contaLigada(contaId);
        TransacaoBanco transacao = transacaoARevisar(conta, transacaoId);
        LancamentoRequest lancamentoRequest = LancamentoRequest.builder()
                .tipo(transacao.getTipo())
                .descricao(criarDoBancoRequest.getDescricao().trim())
                .valor(moeda(transacao.getValor()))
                .data(transacao.getData().format(DATA_BRASIL))
                .categoriaId(criarDoBancoRequest.getCategoriaId())
                .contaId(conta.getId())
                .realizado(transacao.aconteceu(LocalDate.now(clock)))
                .repeticao(Repeticao.UNICA)
                .build();
        List<LancamentoResponse> criados = lancamentoService.inserir(lancamentoRequest);
        transacao.setLancamento(lancamentoService.lancamentoDoUsuario(criados.get(0).getId()));
        transacao.setMovimentacao(null);
        transacao.setSituacao(SituacaoTransacaoBanco.IMPORTADA);
        transacaoBancoRepository.save(transacao);
        return conciliacao(conta, YearMonth.from(transacao.getData()));

    }

    /**
     * Paga a fatura com a conta, na data e no valor da transação, e liga a transação ao
     * pagamento. Recusa se o pendente da fatura já não é o valor do banco.
     */
    @Transactional
    public ConciliacaoContaResponse pagarFatura(Long contaId, Long transacaoId, PagarFaturaContaRequest pagarFaturaContaRequest) {

        Conta conta = contaLigada(contaId);
        TransacaoBanco transacao = transacaoARevisar(conta, transacaoId);
        if (transacao.getTipo() != TipoLancamento.D) {
            throw new ExceptionDefault("Só uma saída da conta paga fatura.");
        }
        Cartao cartao = cartaoService.cartaoDoUsuario(pagarFaturaContaRequest.getCartaoId());
        YearMonth mesFatura = converteStringUtil.toYearMonth(pagarFaturaContaRequest.getAno(), pagarFaturaContaRequest.getMes());
        BigDecimal pendente = faturaService.pendente(cartao, mesFatura);
        if (pendente.compareTo(transacao.getValor()) != 0) {
            throw new ExceptionDefault("O pendente da fatura (" + moeda(pendente) + ") não é o valor do banco (" + moeda(transacao.getValor()) + ").");
        }
        Movimentacao pagamento = faturaService.registrarPagamento(cartao, mesFatura, conta, transacao.getData());
        ligar(conta, transacao, null, pagamento.getId(), false);
        log.info("Conta {}: transação {} pagou a fatura {} do cartão {}", contaId, transacaoId, mesFatura, cartao.getId());
        return conciliacao(conta, YearMonth.from(transacao.getData()));

    }

    /**
     * Lança a diferença entre o saldo do banco e o do sistema como uma receita realizada
     * hoje — o rendimento da aplicação automática, que o saldo do banco já conta e o
     * extrato não traz. Só quando a conciliação deixa (ver {@code rendimento} na resposta).
     */
    @Transactional
    public ConciliacaoContaResponse lancarRendimento(Long contaId, RendimentoContaRequest rendimentoContaRequest) {

        Conta conta = contaLigada(contaId);
        LocalDate hoje = LocalDate.now(clock);
        ConciliacaoContaResponse atual = conciliacao(conta, YearMonth.from(hoje));
        if (atual.getRendimento() == null) {
            throw new ExceptionDefault("Só dá para lançar o rendimento com tudo conciliado e o saldo do banco acima do sistema.");
        }
        LancamentoRequest lancamentoRequest = LancamentoRequest.builder()
                .tipo(TipoLancamento.R)
                .descricao("Rendimento")
                .valor(moeda(atual.getRendimento()))
                .data(hoje.format(DATA_BRASIL))
                .categoriaId(rendimentoContaRequest.getCategoriaId())
                .contaId(conta.getId())
                .realizado(true)
                .repeticao(Repeticao.UNICA)
                .build();
        List<LancamentoResponse> criados = lancamentoService.inserir(lancamentoRequest);
        Lancamento rendimento = lancamentoService.lancamentoDoUsuario(criados.get(0).getId());
        rendimento.setAjusteBanco(true);
        lancamentoRepository.save(rendimento);
        log.info("Conta {}: rendimento de {} lançado para igualar ao saldo do banco", contaId, atual.getRendimento());
        return conciliacao(conta, YearMonth.from(hoje));

    }

    @Transactional
    public ConciliacaoContaResponse ignorar(Long contaId, Long transacaoId) {

        Conta conta = contaLigada(contaId);
        TransacaoBanco transacao = transacaoARevisar(conta, transacaoId);
        ignorar(transacao);
        return conciliacao(conta, YearMonth.from(transacao.getData()));

    }

    /** Volta a transação para revisar. O lançamento continua como está (realizado, se foi). */
    @Transactional
    public ConciliacaoContaResponse desfazer(Long contaId, Long transacaoId) {

        Conta conta = contaLigada(contaId);
        TransacaoBanco transacao = transacaoBancoRepository.findByIdAndConta(transacaoId, conta)
                .orElseThrow(() -> new ExceptionNotFound("Transação do banco não encontrada: " + transacaoId));
        if (aRevisar(transacao)) {
            throw new ExceptionDefault("Esta transação ainda não foi revisada.");
        }
        transacao.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
        transacao.setLancamento(null);
        transacao.setMovimentacao(null);
        transacaoBancoRepository.save(transacao);
        return conciliacao(conta, YearMonth.from(transacao.getData()));

    }

    private ConciliacaoContaResponse conciliacao(Conta conta, YearMonth mes) {

        LocalDate inicio = mes.atDay(1).isBefore(conta.getBancoInicio()) ? conta.getBancoInicio() : mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();
        // As fixas do mês (e da margem) precisam existir para casar com o banco.
        recorrenciaService.gerarAte(conta.getUsuario(), fim.plusDays(MARGEM_DIAS));

        List<TransacaoBanco> transacoes = transacaoBancoRepository.findAllByContaAndDataBetweenOrderByDataDescIdDesc(conta, mes.atDay(1), fim);
        List<TransacaoBanco> aRevisar = transacoes.stream().filter(ConciliacaoContaService::aRevisar).toList();

        // Candidatos: o que não está ligado, de perto do mês. O realizado antes da ligação
        // ficou de fora da busca no banco e não tem com quem casar.
        Set<Long> lancamentosLigados = transacaoBancoRepository.lancamentosLigados(conta);
        Set<Long> movimentacoesLigadas = transacaoBancoRepository.movimentacoesLigadas(conta);
        LocalDate de = inicio.minusDays(MARGEM_DIAS);
        LocalDate ate = fim.plusDays(MARGEM_DIAS);
        List<ConciliadorConta.Item> itens = Stream.concat(
                lancamentoRepository.findAllByContaAndDataBetweenOrderByDataAscIdAsc(conta, de, ate).stream()
                        .filter(l -> !lancamentosLigados.contains(l.getId()) && !Boolean.TRUE.equals(l.getAjusteBanco()))
                        .filter(l -> !(Boolean.TRUE.equals(l.getRealizado()) && l.getData().isBefore(conta.getBancoInicio())))
                        .map(ConciliadorConta.Item::de),
                movimentacaoRepository.findDaContaNoPeriodo(conta.getUsuario(), conta, de, ate).stream()
                        .filter(m -> !movimentacoesLigadas.contains(m.getId()) && !m.getData().isBefore(conta.getBancoInicio()))
                        .map(m -> ConciliadorConta.Item.de(m, conta))).toList();

        ConciliadorConta.Resultado resultado = ConciliadorConta.sugerir(aRevisar, itens);

        List<LinhaConciliacaoContaResponse> linhas = new ArrayList<>();
        FaturasAPagar faturasAPagar = new FaturasAPagar(conta);
        resultado.sugestoes().forEach(s -> {
            List<FaturaResumidaResponse> faturas = s.tipo() == SugestaoConciliacao.CRIAR ? faturasAPagar.comValor(s.transacao()) : List.of();
            SugestaoConciliacao tipo = faturas.isEmpty() ? s.tipo() : SugestaoConciliacao.PAGAR_FATURA;
            linhas.add(new LinhaConciliacaoContaResponse(transacaoBancoMapper.mapObject(s.transacao()),
                    tipo, s.candidatos().stream().map(ConciliacaoContaService::item).toList(), null,
                    tipo == SugestaoConciliacao.CRIAR ? categoriaSugerida(conta, s.transacao()) : null, faturas));
        });
        transacoes.stream().filter(t -> !aRevisar(t)).forEach(t -> linhas.add(new LinhaConciliacaoContaResponse(transacaoBancoMapper.mapObject(t),
                null, List.of(), ligado(t), null, List.of())));

        // Só no sistema: do mês, a partir da ligação e até hoje — o futuro ainda não aconteceu.
        LocalDate hoje = LocalDate.now(clock);
        LocalDate limite = fim.isAfter(hoje) ? hoje : fim;
        List<ItemContaResponse> semPar = resultado.semPar().stream()
                .filter(i -> !i.data().isBefore(inicio) && !i.data().isAfter(limite))
                .map(ConciliacaoContaService::item).toList();

        BigDecimal saldoSistema = saldoService.saldo(conta);
        BigDecimal rendimento = rendimento(conta, mes, saldoSistema, semPar);
        Long categoriaRendimentoId = rendimento == null ? null : lancamentoRepository.findFirstByContaAndAjusteBancoTrueOrderByDataDescIdDesc(conta)
                .map(Lancamento::getCategoria).filter(Categoria::getAtivo).map(Categoria::getId).orElse(null);

        return new ConciliacaoContaResponse(linhas, semPar, conta.getBancoSaldo(), saldoSistema,
                conta.getBancoSincronizado(), conta.getBancoInicio(), rendimento, categoriaRendimentoId);

    }

    /**
     * O que falta no sistema para chegar ao saldo do banco, se só pode ser rendimento: no mês
     * atual, sem transação a conciliar até hoje (em mês nenhum) e sem nada só no sistema.
     * Com algo por revisar, a diferença pode ser outra coisa. Senão, null.
     */
    private BigDecimal rendimento(Conta conta, YearMonth mes, BigDecimal saldoSistema, List<ItemContaResponse> semPar) {

        LocalDate hoje = LocalDate.now(clock);
        if (!mes.equals(YearMonth.from(hoje)) || conta.getBancoSaldo() == null || saldoSistema == null || !semPar.isEmpty()) {
            return null;
        }
        BigDecimal diferenca = conta.getBancoSaldo().subtract(saldoSistema);
        if (diferenca.signum() <= 0 || !transacaoBancoRepository.datasAConciliar(conta, hoje).isEmpty()) {
            return null;
        }
        return diferenca;

    }

    /**
     * Faturas não pagas com o pendente igual ao valor de uma saída da conta: de qualquer
     * cartão do usuário (a conta padrão do cartão não importa, paga-se de onde quiser), com
     * vencimento no mês da transação, no anterior ou no seguinte. Os pendentes são
     * calculados uma vez por fatura.
     */
    private final class FaturasAPagar {

        private final Conta conta;
        private final Map<Cartao, Map<YearMonth, BigDecimal>> pendentes = new IdentityHashMap<>();
        private List<Cartao> cartoes;

        private FaturasAPagar(Conta conta) {
            this.conta = conta;
        }

        List<FaturaResumidaResponse> comValor(TransacaoBanco transacao) {

            if (transacao.getTipo() != TipoLancamento.D) {
                return List.of();
            }
            if (cartoes == null) {
                cartoes = cartaoRepository.findAllByUsuarioOrderByOrdemAscNomeAsc(conta.getUsuario());
            }
            YearMonth mes = YearMonth.from(transacao.getData());
            List<FaturaResumidaResponse> faturas = new ArrayList<>();
            for (Cartao cartao : cartoes) {
                for (YearMonth mesFatura : List.of(mes.minusMonths(1), mes, mes.plusMonths(1))) {
                    BigDecimal pendente = pendentes.computeIfAbsent(cartao, c -> new HashMap<>())
                            .computeIfAbsent(mesFatura, m -> faturaService.pendente(cartao, m));
                    if (pendente.compareTo(transacao.getValor()) == 0) {
                        faturas.add(FaturaResumidaResponse.builder()
                                .cartaoId(cartao.getId())
                                .cartaoNome(cartao.getNome())
                                .vencimento(CalendarioUtil.vencimentoNoMes(mesFatura, cartao.getDiaVencimento()))
                                .pendente(pendente)
                                .build());
                    }
                }
            }
            return faturas;

        }

    }

    private void ligar(Conta conta, TransacaoBanco transacao, Long lancamentoId, Long movimentacaoId, boolean corrigirValor) {

        if ((lancamentoId == null) == (movimentacaoId == null)) {
            throw new ExceptionDefault("Informe um lançamento ou uma movimentação.");
        }
        if (movimentacaoId != null) {
            Movimentacao movimentacao = movimentacaoLivre(conta, transacao, movimentacaoId);
            transacao.setMovimentacao(movimentacao);
            transacao.setLancamento(null);
        } else {
            Lancamento lancamento = lancamentoLivre(conta, transacao, lancamentoId);
            boolean aconteceu = transacao.aconteceu(LocalDate.now(clock));
            if (corrigirValor && lancamento.getValor().compareTo(transacao.getValor()) != 0) {
                if (lancamento.getParcelamento() != null) {
                    throw new ExceptionDefault("O valor de uma parcela não é corrigido por aqui. Edite a compra parcelada.");
                }
                lancamentoService.atualizar(lancamento.getId(), requisicao(lancamento, conta, transacao, aconteceu), Escopo.UNICO);
                log.info("Lançamento {} corrigido para o valor do banco: {}", lancamento.getId(), transacao.getValor());
            }
            // O dinheiro saiu (ou entrou) no dia em que o banco diz. Pendente, só liga:
            // ContaBancoService.sincronizar realiza quando o banco efetivar.
            if (aconteceu) {
                lancamento.setRealizado(true);
                lancamento.setData(transacao.getData());
                lancamentoRepository.save(lancamento);
            }
            transacao.setLancamento(lancamento);
            transacao.setMovimentacao(null);
        }
        transacao.setSituacao(SituacaoTransacaoBanco.CONCILIADA);
        transacaoBancoRepository.save(transacao);

    }

    private void ignorar(TransacaoBanco transacao) {

        transacao.setLancamento(null);
        transacao.setMovimentacao(null);
        transacao.setSituacao(SituacaoTransacaoBanco.IGNORADA);
        transacaoBancoRepository.save(transacao);

    }

    /** O lançamento precisa ser da conta, do mesmo tipo e ainda sem transação ligada. */
    private Lancamento lancamentoLivre(Conta conta, TransacaoBanco transacao, Long lancamentoId) {

        Lancamento lancamento = lancamentoService.lancamentoDoUsuario(lancamentoId);
        if (lancamento.getConta() == null || !lancamento.getConta().getId().equals(conta.getId())) {
            throw new ExceptionDefault("O lançamento não é desta conta.");
        }
        if (lancamento.getTipo() != transacao.getTipo()) {
            throw new ExceptionDefault("O lançamento e a transação do banco não são do mesmo tipo.");
        }
        if (transacaoBancoRepository.existsByLancamento(lancamento)) {
            throw new ExceptionDefault("O lançamento já está ligado a outra transação do banco.");
        }
        return lancamento;

    }

    /** A movimentação precisa mexer nesta conta, no mesmo sentido, com o mesmo valor, e estar livre. */
    private Movimentacao movimentacaoLivre(Conta conta, TransacaoBanco transacao, Long movimentacaoId) {

        Movimentacao movimentacao = movimentacaoRepository.findByIdAndUsuario(movimentacaoId, conta.getUsuario())
                .orElseThrow(() -> new ExceptionNotFound("Movimentação não encontrada: " + movimentacaoId));
        boolean daConta = Stream.of(movimentacao.getContaOrigem(), movimentacao.getContaDestino())
                .anyMatch(c -> c != null && c.getId().equals(conta.getId()));
        if (!daConta) {
            throw new ExceptionDefault("A movimentação não é desta conta.");
        }
        ConciliadorConta.Item item = ConciliadorConta.Item.de(movimentacao, conta);
        if (item.tipo() != transacao.getTipo()) {
            throw new ExceptionDefault("A movimentação e a transação do banco não vão no mesmo sentido.");
        }
        if (movimentacao.getValor().compareTo(transacao.getValor()) != 0) {
            throw new ExceptionDefault("A movimentação e a transação do banco têm valores diferentes.");
        }
        if (transacaoBancoRepository.existsByMovimentacao(movimentacao)) {
            throw new ExceptionDefault("A movimentação já está ligada a outra transação do banco.");
        }
        return movimentacao;

    }

    private Long categoriaSugerida(Conta conta, TransacaoBanco transacao) {

        if (transacao.getCategoriaBanco() == null) {
            return null;
        }
        return transacaoBancoRepository.findFirstByUsuarioAndCategoriaBancoAndLancamentoNotNullOrderByAtualizadoDesc(
                        conta.getUsuario(), transacao.getCategoriaBanco())
                .map(t -> t.getLancamento().getCategoria())
                .filter(categoria -> categoria.getAtivo() && categoria.getTipo() == transacao.getTipo())
                .map(Categoria::getId).orElse(null);

    }

    private TransacaoBanco transacaoARevisar(Conta conta, Long transacaoId) {

        TransacaoBanco transacao = transacaoBancoRepository.findByIdAndConta(transacaoId, conta)
                .orElseThrow(() -> new ExceptionNotFound("Transação do banco não encontrada: " + transacaoId));
        if (!aRevisar(transacao)) {
            throw new ExceptionDefault("Esta transação já foi revisada.");
        }
        return transacao;

    }

    private Conta contaLigada(Long contaId) {

        if (!pluggyClient.liberadoPara(authenticationCurrentUserService.getCurrentUser().getId())) {
            throw new ExceptionDefault("A integração com o banco não está disponível.");
        }
        Conta conta = contaService.contaDoUsuario(contaId);
        if (conta.getBancoContaId() == null) {
            throw new ExceptionDefault("A conta não está ligada ao banco.");
        }
        return conta;

    }

    private static ItemContaResponse ligado(TransacaoBanco transacao) {
        if (transacao.getLancamento() != null) {
            return item(ConciliadorConta.Item.de(transacao.getLancamento()));
        }
        return transacao.getMovimentacao() == null ? null : item(ConciliadorConta.Item.de(transacao.getMovimentacao(), transacao.getConta()));
    }

    private static ItemContaResponse item(ConciliadorConta.Item item) {
        Lancamento l = item.lancamento();
        if (l != null) {
            return new ItemContaResponse(l.getId(), null, item.tipo(), l.getDescricao(), item.valor(), item.data(), l.getRealizado(),
                    l.getRecorrencia() == null ? null : l.getRecorrencia().getId(), l.getParcela(), l.getTotalParcelas());
        }
        Movimentacao m = item.movimentacao();
        return new ItemContaResponse(null, m.getId(), item.tipo(), descricao(m), item.valor(), item.data(), true, null, null, null);
    }

    /** Movimentação sem descrição: o que ela é, para quem olha a conta. */
    private static String descricao(Movimentacao m) {
        if (m.getDescricao() != null && !m.getDescricao().isBlank()) {
            return m.getDescricao();
        }
        return switch (m.getTipo()) {
            case DEPOSITO -> "Depósito";
            case SAQUE -> "Saque";
            case PAGAMENTO_FATURA -> "Pagamento da fatura" + (m.getCartao() == null ? "" : " " + m.getCartao().getNome());
            case TRANSFERENCIA -> "Transferência" + (m.getContaOrigem() != null && m.getContaDestino() != null
                    ? " " + m.getContaOrigem().getNome() + " → " + m.getContaDestino().getNome() : "");
        };
    }

    /**
     * O mesmo lançamento com o valor do banco — para LancamentoService.atualizar. A data e a
     * realização do banco só se a transação já aconteceu; senão ficam as do lançamento.
     */
    private static LancamentoRequest requisicao(Lancamento lancamento, Conta conta, TransacaoBanco transacao, boolean aconteceu) {

        return LancamentoRequest.builder()
                .tipo(lancamento.getTipo())
                .descricao(lancamento.getDescricao())
                .valor(moeda(transacao.getValor()))
                .data((aconteceu ? transacao.getData() : lancamento.getData()).format(DATA_BRASIL))
                .categoriaId(lancamento.getCategoria().getId())
                .contaId(conta.getId())
                .realizado(aconteceu || Boolean.TRUE.equals(lancamento.getRealizado()))
                .build();

    }

    /** 1234.5 → "1234,50", o formato que os requests de lançamento recebem. */
    private static String moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static boolean aRevisar(TransacaoBanco transacao) {
        return transacao.getSituacao() == SituacaoTransacaoBanco.A_REVISAR
                || (transacao.getSituacao() != SituacaoTransacaoBanco.IGNORADA
                && transacao.getLancamento() == null && transacao.getMovimentacao() == null);
    }

}
