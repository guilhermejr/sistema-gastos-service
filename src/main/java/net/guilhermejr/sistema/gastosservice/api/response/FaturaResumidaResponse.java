package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um cartão com a fatura do dashboard: a fechada ainda não paga, senão a atual. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FaturaResumidaResponse {

    private Long cartaoId;
    private String cartaoNome;
    private LocalDate vencimento;
    private LocalDate fechamento;
    private StatusFatura status;
    private BigDecimal total;
    private BigDecimal pendente;
    /** Só no dashboard, em cartão ligado ao banco com algo a conciliar; nulo nos outros casos. */
    private PendenciaConciliacaoResponse conciliacao;
    /** Só no dashboard, quando esta é uma fatura fechada ainda não paga: a atual, aberta. Fora do total de faturas. */
    private FaturaResumidaResponse proxima;

}
