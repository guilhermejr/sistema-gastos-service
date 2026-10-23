package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/** Diferença entre o saldo do banco e o do sistema lançada como rendimento: o valor vem do servidor. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RendimentoContaRequest {

    @NotNull
    private Long categoriaId;

}
