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
    private Boolean somaSaldoGeral;
    private Boolean ativo;

}
