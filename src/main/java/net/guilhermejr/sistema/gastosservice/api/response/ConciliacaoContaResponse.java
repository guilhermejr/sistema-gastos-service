package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** O mês da conta no sistema lado a lado com o extrato do banco. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConciliacaoContaResponse {

    private List<LinhaConciliacaoContaResponse> linhas;
    /** Lançamentos e movimentações do mês, até hoje, sem transação do banco ligada nem sugerida. */
    private List<ItemContaResponse> semPar;
    /** Saldo no banco na última busca. */
    private BigDecimal saldoBanco;
    /** Saldo da conta no sistema agora. */
    private BigDecimal saldoSistema;
    private LocalDateTime sincronizado;
    /** Primeiro dia buscado no banco. */
    private LocalDate inicio;
    /**
     * Quanto o banco está acima do sistema, quando dá para lançar como rendimento: mês
     * atual, nada a conciliar até hoje e nada só no sistema. Senão, null.
     */
    private BigDecimal rendimento;
    /** Categoria do último rendimento lançado assim, para sugerir. */
    private Long categoriaRendimentoId;

}
