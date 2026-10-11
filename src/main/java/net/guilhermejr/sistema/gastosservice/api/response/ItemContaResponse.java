package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um lançamento ou uma movimentação da conta, do ponto de vista dela: D = saiu, R = entrou.
 * Só um dos ids vem preenchido.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ItemContaResponse {

    private Long lancamentoId;
    private Long movimentacaoId;
    private TipoLancamento tipo;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private Boolean realizado;
    /** Lançamento fixo: a série a que pertence. */
    private Long recorrenciaId;
    private Integer parcela;
    private Integer totalParcelas;

}
