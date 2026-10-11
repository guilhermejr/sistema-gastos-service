package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.response.ConexaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Quando a Pluggy atualizou os dados do banco pela última vez, e o que precisa de atenção:
 * erro na conexão, atualização automática desligada, dados velhos ou consentimento vencendo.
 */
@RequiredArgsConstructor
@Service
public class ConexaoBancoService {

    /** A atualização é diária; passou disso com folga, algo parou. */
    static final Duration ATRASO = Duration.ofHours(26);
    static final Duration AVISO_CONSENTIMENTO = Duration.ofDays(30);
    private static final Set<String> COM_ERRO = Set.of("LOGIN_ERROR", "OUTDATED", "WAITING_USER_INPUT");
    private static final ZoneId FUSO = ZoneId.of("America/Bahia");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PluggyClient pluggyClient;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;
    private final Clock clock;

    public ConexaoBancoResponse conexao() {

        if (!pluggyClient.liberadoPara(authenticationCurrentUserService.getCurrentUser().getId())) {
            return new ConexaoBancoResponse(false, null, null, null, null, List.of());
        }
        PluggyClient.Item item = pluggyClient.item(pluggyClient.autenticar());
        return new ConexaoBancoResponse(true, item.status(), utc(item.lastUpdatedAt()), utc(item.nextAutoSyncAt()),
                utc(item.consentExpiresAt()), alertas(item, clock.instant()));

    }

    static List<String> alertas(PluggyClient.Item item, Instant agora) {

        List<String> alertas = new ArrayList<>();
        if (COM_ERRO.contains(item.status())) {
            alertas.add("A conexão com o banco precisa de atenção no Meu Pluggy (situação " + item.status() + ").");
        }
        if (item.autoSyncDisabledAt() != null) {
            alertas.add("A atualização automática da Pluggy foi desligada. Atualize a conexão no Meu Pluggy.");
        }
        if (item.lastUpdatedAt() != null && item.lastUpdatedAt().toInstant().plus(ATRASO).isBefore(agora)) {
            alertas.add("Os dados do banco não são atualizados na Pluggy desde "
                    + item.lastUpdatedAt().atZoneSameInstant(FUSO).format(DATA_HORA) + ".");
        }
        if (item.consentExpiresAt() != null && item.consentExpiresAt().toInstant().minus(AVISO_CONSENTIMENTO).isBefore(agora)) {
            alertas.add("O consentimento com o banco vence em " + item.consentExpiresAt().atZoneSameInstant(FUSO).format(DATA)
                    + ". Renove no Meu Pluggy.");
        }
        return alertas;

    }

    private static LocalDateTime utc(OffsetDateTime dataHora) {
        return dataHora == null ? null : LocalDateTime.ofInstant(dataHora.toInstant(), ZoneOffset.UTC);
    }

}
