package net.guilhermejr.sistema.gastosservice.api.request;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.DataBrasil;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PagamentoFaturaRequest {

    /** Sem conta, usa a conta padrão do cartão. */
    private Long contaId;

    /** Sem data, usa hoje. */
    @DataBrasil
    private String data;

}
