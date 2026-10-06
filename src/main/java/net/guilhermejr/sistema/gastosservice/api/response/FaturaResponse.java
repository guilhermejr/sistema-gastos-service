package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FaturaResponse {

    private CartaoResumidoResponse cartao;
    private LocalDate vencimento;
    private LocalDate fechamento;
    private StatusFatura status;
    /** Despesas menos receitas (estornos) da fatura. */
    private BigDecimal total;
    private BigDecimal pago;
    private BigDecimal pendente;
    private List<LancamentoResponse> lancamentos;
    private List<MovimentacaoResponse> pagamentos;

}
