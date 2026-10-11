package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** O que mudou numa busca das transações da conta corrente no banco. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SincronizacaoContaResponse {

    private int novas;
    private int atualizadas;
    /** Pendentes que o banco deixou de informar. */
    private int removidas;
    /** Transações do banco a partir do dia em que a conta foi ligada. */
    private int total;
    /** Quando foi esta busca, em UTC. */
    private LocalDateTime sincronizado;
    /** Saldo no banco nesta busca; null quando o banco não informou. */
    private BigDecimal saldo;

}
