package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** Mês de vencimento da fatura para onde a compra vai. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FaturaDestinoRequest {

    @NotNull
    private Integer ano;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer mes;

}
