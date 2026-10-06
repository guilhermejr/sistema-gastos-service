package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RealizadoRequest {

    @NotNull
    private Boolean realizado;

}
