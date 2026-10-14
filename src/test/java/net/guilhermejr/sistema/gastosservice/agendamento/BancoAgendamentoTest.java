package net.guilhermejr.sistema.gastosservice.agendamento;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

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

    @Test
    @DisplayName("Avisa só quando a busca aumentou o que há para conciliar")
    public void aviso_conciliar() {

        Assertions.assertEquals(Optional.of("Itaú: 1 nova · 4 a conciliar"), BancoAgendamento.aviso("Itaú", 3, 4));
        Assertions.assertEquals(Optional.of("Conta: 2 novas · 2 a conciliar"), BancoAgendamento.aviso("Conta", 0, 2));
        Assertions.assertEquals(Optional.empty(), BancoAgendamento.aviso("Itaú", 3, 3));
        Assertions.assertEquals(Optional.empty(), BancoAgendamento.aviso("Itaú", 3, 1));

    }

}
