package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CartaoBancoRequest {

    /** Id do cartão no banco (conta de crédito na Pluggy); vazio desliga. */
    @Size(max = 64)
    private String bancoContaId;

}
