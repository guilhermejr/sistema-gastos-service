package net.guilhermejr.sistema.gastosservice.agendamento;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class BancoAgendamentoTest {

    /** 09:00:48 na Bahia. */
    private static final OffsetDateTime PLUGGY = OffsetDateTime.parse("2026-10-11T12:00:48Z");

    @Test
    @DisplayName("Busca quando a Pluggy atualizou depois da última busca, ou quando nunca buscou")
    public void busca_quando_ha_dado_novo() {

        Assertions.assertTrue(BancoAgendamento.desatualizado(LocalDateTime.parse("2026-10-10T18:00:00"), PLUGGY));
        Assertions.assertTrue(BancoAgendamento.desatualizado(null, PLUGGY));

    }

    @Test
    @DisplayName("Não busca de novo o que já foi buscado depois da atualização, nem sem a hora da Pluggy")
    public void nao_busca_sem_dado_novo() {

        Assertions.assertFalse(BancoAgendamento.desatualizado(LocalDateTime.parse("2026-10-11T12:05:00"), PLUGGY));
        Assertions.assertFalse(BancoAgendamento.desatualizado(null, null));

    }

}
