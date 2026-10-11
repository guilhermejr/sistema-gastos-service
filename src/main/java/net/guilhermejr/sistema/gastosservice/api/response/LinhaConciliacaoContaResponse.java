package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;

import java.util.List;

/**
 * Uma transação do banco na conciliação da conta. A revisar: vem com a sugestão e os
 * candidatos. Já revisada: sem sugestão, com o lançamento ou a movimentação ligada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LinhaConciliacaoContaResponse {

    private TransacaoBancoResponse transacao;
    private SugestaoConciliacao sugestao;
    private List<ItemContaResponse> candidatos;
    private ItemContaResponse ligado;
    /** Para criar: a categoria usada da última vez com a mesma categoria do banco. */
    private Long categoriaSugeridaId;

}
