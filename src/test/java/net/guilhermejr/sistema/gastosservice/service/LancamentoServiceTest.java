package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.CartaoMapper;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.FaturaDestinoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.Repeticao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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

    /**
     * Serviço com o CartaoService e a conversão de verdade, para a inclusão em cartão.
     * O cartão vence dia 10 e fecha dia 3: compra em 07/10 cai na fatura de novembro.
     */
    private LancamentoService comCartao() {

        Cartao cartao = new Cartao();
        cartao.setId(5L);
        cartao.setDiaVencimento(10);
        cartao.setDiasFechamento(7);
        cartao.setAtivo(true);
        CartaoRepository cartaoRepository = Mockito.mock(CartaoRepository.class);
        Mockito.when(cartaoRepository.findByIdAndUsuario(Mockito.eq(5L), Mockito.any())).thenReturn(Optional.of(cartao));

        AuthenticationCurrentUserService usuario = Mockito.mock(AuthenticationCurrentUserService.class, Answers.RETURNS_DEEP_STUBS);
        CartaoService cartaoService = new CartaoService(cartaoRepository, lancamentoRepository, Mockito.mock(RecorrenciaRepository.class),
                Mockito.mock(ContaService.class), Mockito.mock(CartaoMapper.class), usuario);
        CategoriaService categoriaService = Mockito.mock(CategoriaService.class);
        Mockito.when(categoriaService.categoriaParaLancamento(Mockito.any(), Mockito.any())).thenReturn(new Categoria());
        Mockito.when(lancamentoRepository.saveAll(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        return new LancamentoService(lancamentoRepository, Mockito.mock(RecorrenciaService.class), categoriaService,
                Mockito.mock(ContaService.class), cartaoService, Mockito.mock(LancamentoMapper.class), new ConverteStringUtil(),
                usuario, Clock.systemDefaultZone());

    }

    private LancamentoRequest compraNoCartao(Repeticao repeticao, Integer parcelas, int ano, int mes) {

        return LancamentoRequest.builder().tipo(TipoLancamento.D).descricao("Compra").valor("300,00").data("07/10/2026")
                .categoriaId(1L).cartaoId(5L).repeticao(repeticao).parcelas(parcelas)
                .fatura(new FaturaDestinoRequest(ano, mes)).build();

    }

    @Test
    @DisplayName("Inclusão em cartão entra na fatura escolhida, mesmo a anterior à natural")
    public void inclusao_entra_na_fatura_escolhida() {

        comCartao().inserir(compraNoCartao(Repeticao.UNICA, null, 2026, 10));

        ArgumentCaptor<Lancamento> salvo = ArgumentCaptor.forClass(Lancamento.class);
        Mockito.verify(lancamentoRepository).save(salvo.capture());
        Assertions.assertEquals(LocalDate.of(2026, 10, 10), salvo.getValue().getFatura());
        Assertions.assertTrue(salvo.getValue().getFaturaTransferida());

    }

    @Test
    @DisplayName("Inclusão parcelada leva todas as parcelas para as faturas seguintes")
    @SuppressWarnings("unchecked")
    public void inclusao_parcelada_desloca_todas_as_parcelas() {

        comCartao().inserir(compraNoCartao(Repeticao.PARCELADA, 3, 2026, 12));

        ArgumentCaptor<List<Lancamento>> salvas = ArgumentCaptor.forClass(List.class);
        Mockito.verify(lancamentoRepository).saveAll(salvas.capture());
        Assertions.assertEquals(List.of(LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10), LocalDate.of(2027, 2, 10)),
                salvas.getValue().stream().map(Lancamento::getFatura).toList());
        Assertions.assertTrue(salvas.getValue().stream().allMatch(Lancamento::getFaturaTransferida));

    }

    @Test
    @DisplayName("Inclusão em cartão recusa fatura duas antes da natural")
    public void inclusao_recusa_fatura_duas_antes() {

        LancamentoService servico = comCartao();
        Assertions.assertThrows(ExceptionDefault.class, () -> servico.inserir(compraNoCartao(Repeticao.UNICA, null, 2026, 9)));
        Mockito.verify(lancamentoRepository, Mockito.never()).save(Mockito.any());

    }

}
