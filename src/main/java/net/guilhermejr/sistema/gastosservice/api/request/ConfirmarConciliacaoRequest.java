package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/** Várias transações de uma vez: ligar a um lançamento ou, sem lançamento, ignorar. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConfirmarConciliacaoRequest {

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

        /** Vazio: ignorar a transação. */
        private Long lancamentoId;

    }

}
