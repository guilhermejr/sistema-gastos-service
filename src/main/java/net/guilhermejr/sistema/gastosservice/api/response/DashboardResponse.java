package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DashboardResponse {

    private Integer ano;
    private Integer mes;
    /** Ciclo em que hoje cai, o mesmo do relatório de ano/mes. */
    private LocalDate inicio;
    private LocalDate fim;
    private TotaisResponse receitas;
    private TotaisResponse despesas;
    /** Soma das contas ativas marcadas para o saldo geral. */
    private BigDecimal saldoGeral;
    /** Soma de todas as contas ativas. */
    private BigDecimal saldoTotal;
    /** Soma das faturas atuais dos cartões ativos. */
    private BigDecimal faturas;
    private List<ContaResponse> contas;
    private List<FaturaResumidaResponse> cartoes;
    private AgendaResponse proximosPagar;
    private AgendaResponse proximosReceber;

}
