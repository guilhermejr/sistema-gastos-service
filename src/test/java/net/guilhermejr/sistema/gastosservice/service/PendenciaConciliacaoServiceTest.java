package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.PendenciaConciliacaoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Test
    @DisplayName("Conta: conta até o fim do mês, com o que o banco já agendou")
    public void conta_ate_fim_do_mes() {

        TransacaoBancoRepository repository = Mockito.mock(TransacaoBancoRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("America/Bahia"));
        Conta conta = new Conta();
        conta.setBancoContaId("itau");
        Mockito.when(repository.datasAConciliar(conta, LocalDate.parse("2026-10-31"))).thenReturn(List.of(LocalDate.parse("2026-10-27")));

        PendenciaConciliacaoResponse pendencia = new PendenciaConciliacaoService(repository, null, clock).daConta(conta);

        Assertions.assertEquals(1, pendencia.getQuantidade());
        Assertions.assertEquals(10, pendencia.getMes());

    }

}
