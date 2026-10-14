package net.guilhermejr.sistema.gastosservice.agendamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.client.NotificacaoClient;
import net.guilhermejr.sistema.gastosservice.service.AvisoSemanalService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Toda segunda às 8h, avisa por push o que vence na semana. Só em produção, para o
 * desenvolvimento não mandar push a cada subida. O envio fica fora da transação que
 * monta a mensagem: uma falha no notificacao-service não desfaz as fixas geradas.
 */
@Log4j2
@RequiredArgsConstructor
@Profile("prod")
@EnableScheduling
@Component
public class AvisoAgendamento {

    private final AvisoSemanalService avisoSemanalService;
    private final NotificacaoClient notificacaoClient;

    @Scheduled(cron = "0 0 8 * * MON", zone = "America/Bahia")
    public void avisar() {

        try {
            avisoSemanalService.montar().ifPresentOrElse(mensagem -> {
                notificacaoClient.enviarPush(AvisoSemanalService.TITULO, mensagem);
                log.info("Aviso semanal de vencimentos enviado");
            }, () -> log.info("Nada vence nesta semana: aviso semanal não enviado"));
        } catch (Exception e) {
            log.error("Erro ao enviar o aviso semanal de vencimentos: {}", e.getMessage());
        }

    }

}
