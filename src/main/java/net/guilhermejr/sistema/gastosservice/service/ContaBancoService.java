package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.ContaMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ContaBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ContaBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transações da conta corrente buscadas no banco (Itaú, pelo Meu Pluggy), guardadas para
 * conciliar com os lançamentos e movimentações da conta. Buscar não cria nem altera nada
 * na conta. Como no cartão, só o usuário dono das credenciais ({@code pluggyUsuario}) vê
 * e liga contas ao banco.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class ContaBancoService {

    private final PluggyClient pluggyClient;
    private final ContaRepository contaRepository;
    private final TransacaoBancoRepository transacaoBancoRepository;
    private final ContaService contaService;
    private final SaldoService saldoService;
    private final ContaMapper contaMapper;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;
    private final Clock clock;

    /** Contas correntes da conexão com o banco. Vazio para quem não tem a integração. */
    public List<ContaBancoResponse> contasDoBanco() {

        if (!pluggyClient.liberadoPara(usuario())) {
            return List.of();
        }
        String chave = pluggyClient.autenticar();
        return pluggyClient.contas(chave).stream()
                .filter(conta -> "BANK".equals(conta.type()))
                .map(conta -> new ContaBancoResponse(conta.id(), conta.name(), conta.balance(),
                        contaRepository.findByBancoContaId(conta.id()).map(Conta::getId).orElse(null)))
                .toList();

    }

    /**
     * Liga a conta a uma conta corrente do banco, ou desliga com o id vazio. A busca começa
     * no dia da ligação: o que já passou está no saldo inicial ou foi lançado sem o banco.
     */
    @Transactional
    public ContaResponse ligar(Long contaId, ContaBancoRequest contaBancoRequest) {

        verificarLiberado();
        Conta conta = contaService.contaDoUsuario(contaId);
        String bancoContaId = Optional.ofNullable(contaBancoRequest.getBancoContaId()).map(String::trim)
                .filter(id -> !id.isEmpty()).orElse(null);

        if (Objects.equals(bancoContaId, conta.getBancoContaId())) {
            return contaMapper.mapObject(conta, saldoService.saldo(conta));
        }

        // Trocar ou desligar descarta o que veio da conta anterior e ainda não foi revisado.
        List<TransacaoBanco> anteriores = transacaoBancoRepository.findAllByConta(conta);
        if (anteriores.stream().anyMatch(t -> t.getSituacao() != SituacaoTransacaoBanco.A_REVISAR)) {
            throw new ExceptionDefault("A conta já tem transações do banco conciliadas. Não é possível trocar a ligação.");
        }

        if (bancoContaId != null) {
            boolean existe = pluggyClient.contas(pluggyClient.autenticar()).stream()
                    .anyMatch(c -> "BANK".equals(c.type()) && c.id().equals(bancoContaId));
            if (!existe) {
                throw new ExceptionDefault("Conta do banco não encontrada.");
            }
            contaRepository.findByBancoContaId(bancoContaId).ifPresent(outra -> {
                throw new ExceptionDefault("Essa conta do banco já está ligada à conta " + outra.getNome() + ".");
            });
        }

        transacaoBancoRepository.deleteAll(anteriores);
        conta.setBancoContaId(bancoContaId);
        conta.setBancoInicio(bancoContaId == null ? null : LocalDate.now(clock));
        conta.setBancoSincronizado(null);
        conta.setBancoSaldo(null);
        log.info("Conta {} {}", contaId, bancoContaId == null ? "desligada do banco" : "ligada ao banco a partir de " + conta.getBancoInicio());
        Conta contaSave = contaRepository.save(conta);
        return contaMapper.mapObject(contaSave, saldoService.saldo(contaSave));

    }

    @Transactional
    public SincronizacaoContaResponse sincronizar(Long contaId) {

        verificarLiberado();
        return sincronizar(contaService.contaDoUsuario(contaId));

    }

    /**
     * Busca as transações da conta no banco e guarda as de {@code bancoInicio} em diante.
     * A já guardada é atualizada; a pendente que o banco deixou de informar, e que ninguém
     * revisou, é apagada. Situação e revisão nunca são tocadas aqui.
     */
    @Transactional
    public SincronizacaoContaResponse sincronizar(Conta conta) {

        if (conta.getBancoContaId() == null) {
            throw new ExceptionDefault("A conta não está ligada ao banco.");
        }

        String chave = pluggyClient.autenticar();
        List<PluggyClient.Transacao> doBanco = pluggyClient.transacoes(chave, conta.getBancoContaId());
        Map<String, TransacaoBanco> guardadas = transacaoBancoRepository.findAllByConta(conta).stream()
                .collect(Collectors.toMap(TransacaoBanco::getBancoId, Function.identity()));

        Set<String> vistas = new HashSet<>();
        List<TransacaoBanco> salvar = new ArrayList<>();
        int novas = 0;
        int atualizadas = 0;
        for (PluggyClient.Transacao transacao : doBanco) {
            if (BancoService.local(transacao.date()).isBefore(conta.getBancoInicio()) || transacao.amount().signum() == 0) {
                continue;
            }
            vistas.add(transacao.id());
            TransacaoBanco guardada = guardadas.get(transacao.id());
            if (guardada == null) {
                guardada = new TransacaoBanco();
                guardada.setConta(conta);
                guardada.setBancoId(transacao.id());
                guardada.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
                guardada.setUsuario(conta.getUsuario());
                preencher(guardada, transacao);
                salvar.add(guardada);
                novas++;
            } else if (preencher(guardada, transacao)) {
                salvar.add(guardada);
                atualizadas++;
            }
        }
        transacaoBancoRepository.saveAll(salvar);

        List<TransacaoBanco> sumiram = guardadas.values().stream()
                .filter(t -> !vistas.contains(t.getBancoId()) && t.getSituacao() == SituacaoTransacaoBanco.A_REVISAR)
                .toList();
        transacaoBancoRepository.deleteAll(sumiram);
        pluggyClient.contas(chave).stream()
                .filter(c -> c.id().equals(conta.getBancoContaId()))
                .findFirst()
                .ifPresent(c -> conta.setBancoSaldo(c.balance()));
        conta.setBancoSincronizado(LocalDateTime.now(ZoneOffset.UTC));
        contaRepository.save(conta);

        log.info("Conta {}: {} transações do banco ({} novas, {} atualizadas, {} removidas)",
                conta.getId(), vistas.size(), novas, atualizadas, sumiram.size());
        return new SincronizacaoContaResponse(novas, atualizadas, sumiram.size(), vistas.size(),
                conta.getBancoSincronizado(), conta.getBancoSaldo());

    }

    /** Copia os dados do banco para a transação guardada e diz se algo mudou. */
    static boolean preencher(TransacaoBanco guardada, PluggyClient.Transacao transacao) {

        String descricao = Optional.ofNullable(transacao.description()).map(String::trim).orElse("");
        Object[] antes = valores(guardada);
        guardada.setTipo(transacao.amount().signum() < 0 ? TipoLancamento.D : TipoLancamento.R);
        guardada.setDescricao(descricao.length() > 255 ? descricao.substring(0, 255) : descricao);
        guardada.setValor(transacao.amount().abs().setScale(2, RoundingMode.HALF_UP));
        guardada.setData(BancoService.local(transacao.date()));
        guardada.setPendente("PENDING".equals(transacao.status()));
        guardada.setCategoriaBanco(transacao.category());
        return !Arrays.equals(antes, valores(guardada));

    }

    private static Object[] valores(TransacaoBanco t) {
        return new Object[]{t.getTipo(), t.getDescricao(), t.getValor(), t.getData(), t.getPendente(), t.getCategoriaBanco()};
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
