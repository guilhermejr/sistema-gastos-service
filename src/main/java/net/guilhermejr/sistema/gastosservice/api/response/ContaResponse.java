package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ContaResponse {

    private Long id;
    private String nome;
    private BigDecimal saldoInicial;
    /** Saldo inicial mais tudo o que já foi realizado na conta. */
    private BigDecimal saldo;
    /**
     * Saldo no fim do ciclo se tudo o que está pendente até lá nela for realizado. Só vem
     * no dashboard, nas contas do Saldo Geral com {@code mostraSaldoPrevisto}; nos outros
     * casos é nulo.
     */
    private BigDecimal saldoPrevisto;
    private Boolean somaSaldoGeral;
    private Boolean mostraSaldoPrevisto;
    private Boolean ativo;

}
