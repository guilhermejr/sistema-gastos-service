package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.CartaoMapper;
import net.guilhermejr.sistema.gastosservice.api.mapper.TransacaoBancoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.CartaoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.TransacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transações do cartão buscadas no banco (Itaú, pelo Meu Pluggy), guardadas para
 * conciliar com os lançamentos. Buscar não cria nem altera lançamento nenhum.
 *
 * <p>Só o usuário dono das credenciais da Pluggy ({@code pluggyUsuario}) vê e liga
 * cartões ao banco; para os demais a integração não existe.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BancoService {

    /** O banco informa datas em UTC; a compra das 22h de um dia não pode virar o dia seguinte. */
    private static final ZoneId FUSO = ZoneId.of("America/Bahia");

    private final PluggyClient pluggyClient;
    private final CartaoRepository cartaoRepository;
    private final TransacaoBancoRepository transacaoBancoRepository;
    private final CartaoService cartaoService;
    private final FaturaService faturaService;
    private final CartaoMapper cartaoMapper;
    private final TransacaoBancoMapper transacaoBancoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    /** Cartões de crédito da conexão com o banco. Vazio para quem não tem a integração. */
    public List<CartaoBancoResponse> cartoesDoBanco() {

        if (!pluggyClient.liberadoPara(usuario())) {
            return List.of();
        }
        String chave = pluggyClient.autenticar();
        return pluggyClient.contas(chave).stream()
                .filter(conta -> "CREDIT".equals(conta.type()))
                .map(conta -> new CartaoBancoResponse(conta.id(), conta.name(),
                        cartaoRepository.findByBancoContaId(conta.id()).map(Cartao::getId).orElse(null)))
                .toList();

    }

    /**
     * Liga o cartão a um cartão do banco, ou desliga com o id vazio. A busca começa na
     * fatura atual do cartão: o que já passou foi lançado sem o banco e não é conciliado.
     */
    @Transactional
    public CartaoResponse ligar(Long cartaoId, CartaoBancoRequest cartaoBancoRequest) {

        verificarLiberado();
        Cartao cartao = cartaoService.cartaoDoUsuario(cartaoId);
        String bancoContaId = Optional.ofNullable(cartaoBancoRequest.getBancoContaId()).map(String::trim)
                .filter(id -> !id.isEmpty()).orElse(null);

        if (Objects.equals(bancoContaId, cartao.getBancoContaId())) {
            return cartaoMapper.mapObject(cartao);
        }

        // Trocar ou desligar descarta o que veio do cartão anterior e ainda não foi revisado.
        List<TransacaoBanco> anteriores = transacaoBancoRepository.findAllByCartao(cartao);
        if (anteriores.stream().anyMatch(t -> t.getSituacao() != SituacaoTransacaoBanco.A_REVISAR)) {
            throw new ExceptionDefault("O cartão já tem transações do banco conciliadas. Não é possível trocar a ligação.");
        }

        if (bancoContaId == null) {
            transacaoBancoRepository.deleteAll(anteriores);
            cartao.setBancoContaId(null);
            cartao.setBancoInicioFatura(null);
            log.info("Cartão {} desligado do banco", cartaoId);
            return cartaoMapper.mapObject(cartaoRepository.save(cartao));
        }

        boolean existe = pluggyClient.contas(pluggyClient.autenticar()).stream()
                .anyMatch(conta -> "CREDIT".equals(conta.type()) && conta.id().equals(bancoContaId));
        if (!existe) {
            throw new ExceptionDefault("Cartão do banco não encontrado.");
        }
        cartaoRepository.findByBancoContaId(bancoContaId).ifPresent(outro -> {
            throw new ExceptionDefault("Esse cartão do banco já está ligado ao cartão " + outro.getNome() + ".");
        });

        transacaoBancoRepository.deleteAll(anteriores);
        cartao.setBancoContaId(bancoContaId);
        cartao.setBancoInicioFatura(CalendarioUtil.vencimentoNoMes(faturaService.mesDaFaturaAtual(cartao), cartao.getDiaVencimento()));
        log.info("Cartão {} ligado ao banco a partir da fatura de {}", cartaoId, cartao.getBancoInicioFatura());
        return cartaoMapper.mapObject(cartaoRepository.save(cartao));

    }

    @Transactional
    public SincronizacaoBancoResponse sincronizar(Long cartaoId) {

        verificarLiberado();
        return sincronizar(cartaoService.cartaoDoUsuario(cartaoId));

    }

    /**
     * Busca todas as transações do cartão no banco e guarda as das faturas a partir de
     * {@code bancoInicioFatura}. A transação já guardada é atualizada (uma pendente pode
     * mudar de valor ou fechar); a pendente que o banco deixou de informar, e que ninguém
     * revisou, é apagada. Situação e revisão nunca são tocadas aqui.
     */
    @Transactional
    public SincronizacaoBancoResponse sincronizar(Cartao cartao) {

        if (cartao.getBancoContaId() == null) {
            throw new ExceptionDefault("O cartão não está ligado ao banco.");
        }

        String chave = pluggyClient.autenticar();
        Map<String, YearMonth> mesPorFatura = pluggyClient.faturas(chave, cartao.getBancoContaId()).stream()
                .collect(Collectors.toMap(PluggyClient.Fatura::id, fatura -> YearMonth.from(local(fatura.dueDate())), (a, b) -> a));
        List<PluggyClient.Transacao> doBanco = pluggyClient.transacoes(chave, cartao.getBancoContaId());

        YearMonth inicio = YearMonth.from(cartao.getBancoInicioFatura());
        Map<String, TransacaoBanco> guardadas = transacaoBancoRepository.findAllByCartao(cartao).stream()
                .collect(Collectors.toMap(TransacaoBanco::getBancoId, Function.identity()));

        Set<String> vistas = new HashSet<>();
        List<TransacaoBanco> salvar = new ArrayList<>();
        int novas = 0;
        int atualizadas = 0;
        for (PluggyClient.Transacao transacao : doBanco) {
            YearMonth mes = mesDaFatura(transacao, mesPorFatura);
            if (mes == null || mes.isBefore(inicio)) {
                continue;
            }
            vistas.add(transacao.id());
            TransacaoBanco guardada = guardadas.get(transacao.id());
            if (guardada == null) {
                guardada = new TransacaoBanco();
                guardada.setCartao(cartao);
                guardada.setBancoId(transacao.id());
                guardada.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
                guardada.setUsuario(cartao.getUsuario());
                preencher(guardada, transacao, CalendarioUtil.vencimentoNoMes(mes, cartao.getDiaVencimento()));
                salvar.add(guardada);
                novas++;
            } else if (preencher(guardada, transacao, CalendarioUtil.vencimentoNoMes(mes, cartao.getDiaVencimento()))) {
                salvar.add(guardada);
                atualizadas++;
            }
        }
        transacaoBancoRepository.saveAll(salvar);

        List<TransacaoBanco> sumiram = guardadas.values().stream()
                .filter(t -> !vistas.contains(t.getBancoId()) && t.getSituacao() == SituacaoTransacaoBanco.A_REVISAR)
                .toList();
        transacaoBancoRepository.deleteAll(sumiram);

        log.info("Cartão {}: {} transações do banco ({} novas, {} atualizadas, {} removidas)",
                cartao.getId(), vistas.size(), novas, atualizadas, sumiram.size());
        return new SincronizacaoBancoResponse(novas, atualizadas, sumiram.size(), vistas.size());

    }

    /** As transações do banco na fatura do mês, da mais recente para a mais antiga. */
    public List<TransacaoBancoResponse> fatura(Long cartaoId, Integer ano, Integer mes) {

        verificarLiberado();
        Cartao cartao = cartaoService.cartaoDoUsuario(cartaoId);
        YearMonth mesFatura = converteStringUtil.toYearMonth(ano, mes);
        return transacaoBancoMapper.mapList(transacaoBancoRepository.findAllByCartaoAndFaturaBetweenOrderByDataDescIdDesc(
                cartao, mesFatura.atDay(1), mesFatura.atEndOfMonth()));

    }

    /**
     * O mês da fatura em que o banco pôs a transação: o previsto ({@code billForecastDate})
     * ou, sem ele, o vencimento da fatura a que ela pertence. Sem nenhum dos dois, null.
     */
    static YearMonth mesDaFatura(PluggyClient.Transacao transacao, Map<String, YearMonth> mesPorFatura) {

        PluggyClient.DadosCartao dados = transacao.creditCardMetadata();
        if (dados == null) {
            return null;
        }
        if (dados.billForecastDate() != null && !dados.billForecastDate().isBlank()) {
            return YearMonth.parse(dados.billForecastDate());
        }
        return dados.billId() == null ? null : mesPorFatura.get(dados.billId());

    }

    /** Copia os dados do banco para a transação guardada e diz se algo mudou. */
    static boolean preencher(TransacaoBanco guardada, PluggyClient.Transacao transacao, LocalDate fatura) {

        PluggyClient.DadosCartao dados = Optional.ofNullable(transacao.creditCardMetadata())
                .orElse(new PluggyClient.DadosCartao(null, null, null, null, null, null));
        boolean parcelada = dados.totalInstallments() != null && dados.totalInstallments() > 1;
        String descricao = Optional.ofNullable(transacao.description()).map(String::trim).orElse("");

        TipoLancamento tipo = transacao.amount().signum() < 0 ? TipoLancamento.R : TipoLancamento.D;
        Object[] antes = valores(guardada);
        guardada.setTipo(tipo);
        guardada.setDescricao(descricao.length() > 255 ? descricao.substring(0, 255) : descricao);
        guardada.setValor(transacao.amount().abs().setScale(2, RoundingMode.HALF_UP));
        guardada.setData(local(transacao.date()));
        guardada.setDataCompra(dados.purchaseDate() == null ? null : local(dados.purchaseDate()));
        guardada.setFatura(fatura);
        guardada.setParcela(parcelada ? dados.installmentNumber() : null);
        guardada.setTotalParcelas(parcelada ? dados.totalInstallments() : null);
        guardada.setPendente("PENDING".equals(transacao.status()));
        guardada.setFinalCartao(dados.cardNumber());
        guardada.setCategoriaBanco(transacao.category());
        return !Arrays.equals(antes, valores(guardada));

    }

    private static Object[] valores(TransacaoBanco t) {
        return new Object[]{t.getTipo(), t.getDescricao(), t.getValor(), t.getData(), t.getDataCompra(), t.getFatura(),
                t.getParcela(), t.getTotalParcelas(), t.getPendente(), t.getFinalCartao(), t.getCategoriaBanco()};
    }

    private static LocalDate local(OffsetDateTime dataHora) {
        return dataHora.atZoneSameInstant(FUSO).toLocalDate();
    }

    private void verificarLiberado() {

        if (!pluggyClient.liberadoPara(usuario())) {
            throw new ExceptionDefault("A integração com o banco não está disponível.");
        }

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
