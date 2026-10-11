package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;

/** Uma conta corrente do banco, para escolher a qual conta do sistema ligar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ContaBancoResponse {

    private String id;
    private String nome;
    private BigDecimal saldo;
    /** Conta do sistema já ligada a esta, se houver. */
    private Long contaId;

}
