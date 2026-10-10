package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/** A fatura do sistema lado a lado com a do banco. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConciliacaoResponse {

    private List<LinhaConciliacaoResponse> linhas;
    /** Lançamentos da fatura sem transação do banco ligada nem sugerida. */
    private List<LancamentoResponse> semPar;
    /** Compras menos estornos no banco, sem o pagamento da fatura anterior. */
    private BigDecimal totalBanco;
    /** Total da fatura no sistema. */
    private BigDecimal totalSistema;

}
