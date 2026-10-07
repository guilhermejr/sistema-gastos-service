package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public class LancamentoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 7);
    private static final LocalDate PREVISTA = LocalDate.of(2026, 10, 15);

    private LancamentoRepository lancamentoRepository;
    private LancamentoService lancamentoService;

    @BeforeEach
    public void preparar() {

        lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        Mockito.when(lancamentoRepository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        AuthenticationCurrentUserService usuario = Mockito.mock(AuthenticationCurrentUserService.class, Answers.RETURNS_DEEP_STUBS);
        ZoneId zona = ZoneId.of("America/Bahia");
        Clock clock = Clock.fixed(ZonedDateTime.of(HOJE.atTime(10, 0), zona).toInstant(), zona);

        lancamentoService = new LancamentoService(lancamentoRepository, Mockito.mock(RecorrenciaService.class),
                Mockito.mock(CategoriaService.class), Mockito.mock(ContaService.class), Mockito.mock(CartaoService.class),
                Mockito.mock(LancamentoMapper.class), Mockito.mock(ConverteStringUtil.class), usuario, clock);

    }

    private Lancamento emConta(boolean realizado) {

        Lancamento lancamento = new Lancamento();
        lancamento.setData(PREVISTA);
        lancamento.setRealizado(realizado);
        Mockito.when(lancamentoRepository.findByIdAndUsuario(Mockito.eq(1L), Mockito.any())).thenReturn(Optional.of(lancamento));
        return lancamento;

    }

    @Test
    @DisplayName("Efetivar muda a data para hoje")
    public void efetivar_muda_a_data_para_hoje() {

        Lancamento lancamento = emConta(false);
        lancamentoService.alterarRealizado(1L, true);

        Assertions.assertTrue(lancamento.getRealizado());
        Assertions.assertEquals(HOJE, lancamento.getData());

    }

    @Test
    @DisplayName("Desmarcar não mexe na data")
    public void desmarcar_nao_mexe_na_data() {

        Lancamento lancamento = emConta(true);
        lancamentoService.alterarRealizado(1L, false);

        Assertions.assertFalse(lancamento.getRealizado());
        Assertions.assertEquals(PREVISTA, lancamento.getData());

    }

    @Test
    @DisplayName("Marcar de novo o que já está realizado não mexe na data")
    public void marcar_de_novo_nao_mexe_na_data() {

        Lancamento lancamento = emConta(true);
        lancamentoService.alterarRealizado(1L, true);

        Assertions.assertEquals(PREVISTA, lancamento.getData());

    }

}
