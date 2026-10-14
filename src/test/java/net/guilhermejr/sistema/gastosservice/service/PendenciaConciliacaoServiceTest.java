package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.PendenciaConciliacaoResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

public class PendenciaConciliacaoServiceTest {

    @Test
    @DisplayName("Nada a conciliar: sem pendência")
    public void vazio() {

        Assertions.assertNull(PendenciaConciliacaoService.pendencia(List.of()));
        Assertions.assertEquals(0, PendenciaConciliacaoService.quantidade(null));

    }

    @Test
    @DisplayName("Conta as transações e abre no mês mais antigo")
    public void mais_antigo() {

        PendenciaConciliacaoResponse pendencia = PendenciaConciliacaoService.pendencia(List.of(
                LocalDate.parse("2026-11-06"), LocalDate.parse("2026-10-06"), LocalDate.parse("2026-11-06")));

        Assertions.assertEquals(3, pendencia.getQuantidade());
        Assertions.assertEquals(2026, pendencia.getAno());
        Assertions.assertEquals(10, pendencia.getMes());

    }

}
