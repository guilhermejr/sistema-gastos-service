package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

/** Um cartão de crédito do banco, para escolher a qual cartão do sistema ligar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CartaoBancoResponse {

    private String id;
    private String nome;
    /** Cartão do sistema já ligado a este, se houver. */
    private Long cartaoId;

}
