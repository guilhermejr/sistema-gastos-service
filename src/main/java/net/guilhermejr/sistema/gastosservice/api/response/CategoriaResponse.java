package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CategoriaResponse {

    private Long id;
    private String descricao;
    private TipoLancamento tipo;
    private Boolean ativo;

}
