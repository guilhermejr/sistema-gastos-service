package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/** Várias transações da conta de uma vez: ligar a um lançamento ou movimentação, ou ignorar. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConfirmarConciliacaoContaRequest {

    @NotEmpty
    private List<@Valid Item> itens;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Item {

        @NotNull
        private Long transacaoId;

        /** Os dois vazios: ignorar a transação. */
        private Long lancamentoId;

        private Long movimentacaoId;

    }

}
