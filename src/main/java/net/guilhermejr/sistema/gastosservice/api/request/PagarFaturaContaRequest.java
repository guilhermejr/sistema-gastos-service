package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** A fatura que a transação do banco pagou. A conta, a data e o valor vêm da transação. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PagarFaturaContaRequest {

    @NotNull
    private Long cartaoId;

    @NotNull
    private Integer ano;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer mes;

}
