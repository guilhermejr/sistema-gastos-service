package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** O que passou pela conta de {@code inicio} a {@code fim}, com o saldo antes e depois. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ExtratoContaResponse {

    private ContaResumidoResponse conta;
    private LocalDate inicio;
    private LocalDate fim;
    /** Saldo ao fim do dia anterior a {@code inicio}. */
    private BigDecimal saldoAnterior;
    private BigDecimal entradas;
    private BigDecimal saidas;
    /** Saldo ao fim de {@code fim}. */
    private BigDecimal saldoFinal;
    /** Da mais antiga para a mais nova. */
    private List<LinhaExtratoResponse> linhas;

}
