package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConfiguracaoRequest {

    /** Até 28, para o ciclo começar no mesmo dia em todos os meses, fevereiro inclusive. */
    @NotNull
    @Min(1)
    @Max(28)
    private Integer diaInicioCiclo;

}
