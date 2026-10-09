package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.ValorCategoriaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

public class RelatorioServiceTest {

    private static Categoria categoria(long id, String descricao) {

        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setDescricao(descricao);
        return categoria;

    }

    private static Lancamento lancamento(TipoLancamento tipo, Categoria categoria, String valor) {

        Lancamento lancamento = new Lancamento();
        lancamento.setTipo(tipo);
        lancamento.setCategoria(categoria);
        lancamento.setValor(new BigDecimal(valor));
        return lancamento;

    }

    @Test
    @DisplayName("Soma por categoria só o tipo pedido, da maior para a menor")
    public void soma_por_categoria() {

        Categoria mercado = categoria(1, "Mercado");
        Categoria lazer = categoria(2, "Lazer");
        Categoria salario = categoria(3, "Salário");
        List<Lancamento> lancamentos = List.of(
                lancamento(TipoLancamento.D, lazer, "50.00"),
                lancamento(TipoLancamento.D, mercado, "30.00"),
                lancamento(TipoLancamento.R, salario, "1000.00"),
                lancamento(TipoLancamento.D, categoria(1, "Mercado"), "40.00"));

        List<ValorCategoriaResponse> despesas = RelatorioService.porCategoria(lancamentos, TipoLancamento.D);

        Assertions.assertEquals(List.of("Mercado", "Lazer"), despesas.stream().map(ValorCategoriaResponse::getDescricao).toList());
        Assertions.assertEquals(List.of(new BigDecimal("70.00"), new BigDecimal("50.00")), despesas.stream().map(ValorCategoriaResponse::getValor).toList());
        Assertions.assertEquals(List.of(3L), RelatorioService.porCategoria(lancamentos, TipoLancamento.R).stream().map(ValorCategoriaResponse::getCategoriaId).toList());

    }

}
