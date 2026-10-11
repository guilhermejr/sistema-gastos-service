package net.guilhermejr.sistema.gastosservice.api.request;

import lombok.*;

/** Liga a transação do banco a um lançamento ou a uma movimentação da conta (um dos dois). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConciliarContaRequest {

    private Long lancamentoId;
    private Long movimentacaoId;
    /** Passa o valor do lançamento para o do banco (quando os dois diferem). */
    @Builder.Default
    private Boolean corrigirValor = false;

}
