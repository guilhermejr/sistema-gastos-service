package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RelatorioMensalResponse {

    private Integer ano;
    private Integer mes;
    private TotaisResponse entradas;
    private TotaisResponse saidas;
    /** Entradas menos saídas, só do que foi realizado. */
    private BigDecimal saldoRealizado;
    /** Entradas menos saídas, contando o que ainda está pendente. */
    private BigDecimal saldoPrevisto;
    private List<LancamentoResponse> lancamentos;

}
