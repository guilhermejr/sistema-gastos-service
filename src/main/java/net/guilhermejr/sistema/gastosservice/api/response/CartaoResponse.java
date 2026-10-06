package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CartaoResponse {

    private Long id;
    private String nome;
    private Integer diaVencimento;
    private Integer diasFechamento;
    private ContaResumidoResponse conta;
    private Boolean ativo;

}
