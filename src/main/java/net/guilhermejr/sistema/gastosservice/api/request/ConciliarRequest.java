package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConciliarRequest {

    @NotNull
    private Long lancamentoId;

    /** Passa o valor do lançamento para o do banco (quando os dois diferem). */
    @Builder.Default
    private Boolean corrigirValor = false;

}
