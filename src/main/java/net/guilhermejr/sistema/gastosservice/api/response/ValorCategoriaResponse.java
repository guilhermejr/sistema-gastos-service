package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;

/** Quanto uma categoria somou no ciclo, para os gráficos. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ValorCategoriaResponse {

    private Long categoriaId;
    private String descricao;
    private BigDecimal valor;

}
