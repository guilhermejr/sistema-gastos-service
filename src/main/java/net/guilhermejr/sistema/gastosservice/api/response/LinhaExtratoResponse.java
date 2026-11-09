package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do extrato, do ponto de vista da conta: D = saiu, R = entrou. Só um dos ids
 * vem preenchido; {@code movimentacao} diz o tipo quando é uma movimentação.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LinhaExtratoResponse {

    private Long lancamentoId;
    private Long movimentacaoId;
    private TipoMovimentacao movimentacao;
    private TipoLancamento tipo;
    private String descricao;
    private String categoria;
    private BigDecimal valor;
    private LocalDate data;
    /** Saldo da conta depois desta linha. */
    private BigDecimal saldo;

}
