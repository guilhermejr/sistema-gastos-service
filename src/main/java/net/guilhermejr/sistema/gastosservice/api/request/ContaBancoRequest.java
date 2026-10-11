package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ContaBancoRequest {

    /** Id da conta corrente no banco (conta BANK na Pluggy); vazio desliga. */
    @Size(max = 64)
    private String bancoContaId;

}
