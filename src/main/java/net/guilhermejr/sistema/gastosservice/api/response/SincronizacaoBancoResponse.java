package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** O que mudou numa busca de transações no banco. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SincronizacaoBancoResponse {

    private int novas;
    private int atualizadas;
    /** Pendentes que o banco deixou de informar (canceladas ou trocadas pela definitiva). */
    private int removidas;
    /** Transações do banco a partir da primeira fatura buscada. */
    private int total;
    /** Quando foi esta busca, em UTC. */
    private LocalDateTime sincronizado;
    /** Limite total e disponível do cartão nesta busca; null quando o banco não informou. */
    private BigDecimal limite;
    private BigDecimal limiteDisponivel;

}
