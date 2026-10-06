package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item das listas "a pagar" e "a receber". Pode ser um lançamento em conta ou uma
 * fatura de cartão inteira, que é como ela sai da conta.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ItemAgendaResponse {

    public static final String LANCAMENTO = "LANCAMENTO";
    public static final String FATURA = "FATURA";

    private String origem;
    /** Id do lançamento, ou nulo quando é fatura. */
    private Long lancamentoId;
    /** Id do cartão, quando é fatura. */
    private Long cartaoId;
    private String descricao;
    private LocalDate data;
    private BigDecimal valor;
    private Boolean atrasado;

}
