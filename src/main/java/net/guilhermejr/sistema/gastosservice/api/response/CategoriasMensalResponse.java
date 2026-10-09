package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Despesas e receitas de um ciclo somadas por categoria, da maior para a menor. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CategoriasMensalResponse {

    private Integer ano;
    private Integer mes;
    private LocalDate inicio;
    private LocalDate fim;
    private List<ValorCategoriaResponse> despesas;
    private List<ValorCategoriaResponse> receitas;

    /** Estornos e cashback nas faturas do ciclo: descontam do total das faturas, mas não de uma categoria de despesa. */
    private BigDecimal estornosCartao;

}
