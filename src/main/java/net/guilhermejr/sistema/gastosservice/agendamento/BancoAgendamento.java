package net.guilhermejr.sistema.gastosservice.agendamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.service.BancoService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Busca diária das transações dos cartões ligados ao banco. Só em produção: no
 * desenvolvimento a busca é pelo botão, para não consultar o banco a cada subida.
 *
 * <p>O Meu Pluggy atualiza a conexão uma vez por dia, perto do meio-dia; às 14h os dados
 * do dia já chegaram.
 */
@Log4j2
@RequiredArgsConstructor
@Profile("prod")
@EnableScheduling
@Component
public class BancoAgendamento {

    private final CartaoRepository cartaoRepository;
    private final PluggyClient pluggyClient;
    private final BancoService bancoService;

    @Scheduled(cron = "0 0 14 * * *", zone = "America/Bahia")
    public void sincronizar() {

        for (Cartao cartao : cartaoRepository.findAllByBancoContaIdNotNullAndAtivoTrue()) {
            if (!pluggyClient.liberadoPara(cartao.getUsuario())) {
                continue;
            }
            try {
                bancoService.sincronizar(cartao);
            } catch (Exception e) {
                log.error("Erro ao buscar no banco as transações do cartão {}: {}", cartao.getId(), e.getMessage());
            }
        }

    }

}
