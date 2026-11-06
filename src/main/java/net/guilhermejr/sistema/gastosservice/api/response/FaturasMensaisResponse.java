package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Total líquido das faturas de cada cartão, mês a mês, para o gráfico. Os valores de
 * cada cartão seguem a ordem de {@code meses}; mês sem fatura vem zero.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FaturasMensaisResponse {

    private List<Mes> meses;
    private List<FaturasCartao> cartoes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Mes {

        private Integer ano;
        private Integer mes;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class FaturasCartao {

        private Long cartaoId;
        private String nome;
        private List<BigDecimal> valores;

    }

}
