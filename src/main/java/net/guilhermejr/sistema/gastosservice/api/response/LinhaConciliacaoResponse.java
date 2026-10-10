package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;

import java.util.List;

/**
 * Uma transação do banco na conciliação. A revisar: vem com a sugestão e os candidatos.
 * Já revisada: sem sugestão, com o lançamento a que foi ligada (se houver).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LinhaConciliacaoResponse {

    private TransacaoBancoResponse transacao;
    private SugestaoConciliacao sugestao;
    private List<LancamentoResponse> candidatos;
    private LancamentoResponse lancamento;
    /** Para criar: a categoria usada da última vez com a mesma categoria do banco. */
    private Long categoriaSugeridaId;

}
