package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.util.List;

/** Uma página das listas "a pagar" e "a receber", e se há mais itens depois dela. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AgendaResponse {

    private List<ItemAgendaResponse> itens;
    private Boolean temMais;

}
