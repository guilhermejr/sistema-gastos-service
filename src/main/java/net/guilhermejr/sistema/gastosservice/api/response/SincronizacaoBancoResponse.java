package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

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

}
