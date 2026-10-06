package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um cartão com a fatura atual, para o dashboard. */
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

}
