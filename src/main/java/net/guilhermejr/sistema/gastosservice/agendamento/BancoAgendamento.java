package net.guilhermejr.sistema.gastosservice.agendamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.client.NotificacaoClient;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.service.BancoService;
import net.guilhermejr.sistema.gastosservice.service.ContaBancoService;
import net.guilhermejr.sistema.gastosservice.service.PendenciaConciliacaoService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Busca das transações dos cartões e das contas ligados ao banco sempre que a Pluggy tem
 * dados novos. Só em produção: no desenvolvimento a busca é pelo botão, para não consultar
 * o banco a cada subida.
 *
 * <p>O Meu Pluggy atualiza a conexão uma vez por dia, num horário que ele mesmo escolhe e
 * que muda a cada atualização manual — não dá para marcar uma hora fixa logo depois. Por
 * isso, a cada hora, pergunta à Pluggy quando ela atualizou ({@code lastUpdatedAt}) e só
 * sincroniza o que foi sincronizado antes disso. Este app não pode pedir a atualização: a
 * Pluggy responde 400 "MeuPluggy item cant be updated" ao PATCH /items/{id}.
 *
 * <p>Quando a busca aumenta o que há para conciliar (até a fatura atual, no cartão; até
 * hoje, na conta), avisa por push — sem ele, a busca automática passa despercebida. Avisa
 * também, num push à parte, quando a busca mudou o dia de vencimento de um cartão.
 */
@Log4j2
@RequiredArgsConstructor
@Profile("prod")
@EnableScheduling
@Component
public class BancoAgendamento {

    static final String TITULO_AVISO = "Transações para conciliar";
    static final String TITULO_VENCIMENTO = "Vencimento do cartão mudou";

    private final CartaoRepository cartaoRepository;
    private final PluggyClient pluggyClient;
    private final BancoService bancoService;
    private final ContaRepository contaRepository;
    private final ContaBancoService contaBancoService;
    private final PendenciaConciliacaoService pendenciaConciliacaoService;
    private final NotificacaoClient notificacaoClient;

    @Scheduled(cron = "0 5 * * * *", zone = "America/Bahia")
    public void sincronizar() {

        List<Cartao> cartoes = cartaoRepository.findAllByBancoContaIdNotNullAndAtivoTrue().stream()
                .filter(cartao -> pluggyClient.liberadoPara(cartao.getUsuario())).toList();
        List<Conta> contas = contaRepository.findAllByBancoContaIdNotNullAndAtivoTrue().stream()
                .filter(conta -> pluggyClient.liberadoPara(conta.getUsuario())).toList();
        if (cartoes.isEmpty() && contas.isEmpty()) {
            return;
        }

        OffsetDateTime atualizado;
        try {
            atualizado = pluggyClient.item(pluggyClient.autenticar()).lastUpdatedAt();
        } catch (Exception e) {
            log.error("Erro ao consultar a conexão com o banco: {}", e.getMessage());
            return;
        }

        List<String> avisos = new ArrayList<>();
        List<String> vencimentos = new ArrayList<>();
        for (Cartao cartao : cartoes) {
            if (!desatualizado(cartao.getBancoSincronizado(), atualizado)) {
                continue;
            }
            try {
                int antes = PendenciaConciliacaoService.quantidade(pendenciaConciliacaoService.doCartao(cartao));
                Integer novoDia = bancoService.sincronizar(cartao).getNovoDiaVencimento();
                if (novoDia != null) {
                    vencimentos.add(cartao.getNome() + ": agora vence no dia " + novoDia
                            + ". As compras não pagas da fatura em aberto em diante foram reposicionadas.");
                }
                aviso(cartao.getNome(), antes, PendenciaConciliacaoService.quantidade(pendenciaConciliacaoService.doCartao(cartao)))
                        .ifPresent(avisos::add);
            } catch (Exception e) {
                log.error("Erro ao buscar no banco as transações do cartão {}: {}", cartao.getId(), e.getMessage());
            }
        }
        for (Conta conta : contas) {
            if (!desatualizado(conta.getBancoSincronizado(), atualizado)) {
                continue;
            }
            try {
                int antes = PendenciaConciliacaoService.quantidade(pendenciaConciliacaoService.daConta(conta));
                contaBancoService.sincronizar(conta);
                aviso(conta.getNome(), antes, PendenciaConciliacaoService.quantidade(pendenciaConciliacaoService.daConta(conta)))
                        .ifPresent(avisos::add);
            } catch (Exception e) {
                log.error("Erro ao buscar no banco as transações da conta {}: {}", conta.getId(), e.getMessage());
            }
        }

        enviar(TITULO_VENCIMENTO, vencimentos);
        enviar(TITULO_AVISO, avisos);

    }

    private void enviar(String titulo, List<String> linhas) {

        if (linhas.isEmpty()) {
            return;
        }
        try {
            notificacaoClient.enviarPush(titulo, String.join("\n", linhas));
            log.info("Aviso \"{}\" enviado: {}", titulo, linhas);
        } catch (Exception e) {
            log.error("Erro ao enviar o aviso \"{}\": {}", titulo, e.getMessage());
        }

    }

    /** Uma linha do push quando a busca aumentou o que há para conciliar; vazio se não aumentou. */
    static Optional<String> aviso(String nome, int antes, int depois) {
        if (depois <= antes) {
            return Optional.empty();
        }
        int novas = depois - antes;
        return Optional.of(nome + ": " + novas + (novas == 1 ? " nova" : " novas") + " · " + depois + " a conciliar");
    }

    /**
     * Precisa buscar quando nunca buscou ou quando a última busca (UTC) foi antes da última
     * atualização da Pluggy. Sem a hora da Pluggy, não busca.
     */
    static boolean desatualizado(LocalDateTime sincronizado, OffsetDateTime atualizadoPluggy) {
        if (atualizadoPluggy == null) {
            return false;
        }
        return sincronizado == null || sincronizado.isBefore(LocalDateTime.ofInstant(atualizadoPluggy.toInstant(), ZoneOffset.UTC));
    }

}
