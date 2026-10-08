package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LancamentoResponse {

    private Long id;
    private TipoLancamento tipo;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private CategoriaResumidoResponse categoria;
    private ContaResumidoResponse conta;
    private CartaoResumidoResponse cartao;
    /** Vencimento da fatura, quando é lançamento de cartão. */
    private LocalDate fatura;
    /** Foi posta à mão numa fatura que não é a da data da compra. */
    private Boolean faturaTransferida;
    private Boolean realizado;
    private Integer parcela;
    private Integer totalParcelas;
    private UUID parcelamento;
    /** Preenchido quando o lançamento é uma ocorrência de despesa/receita fixa. */
    private Long recorrenciaId;

}
