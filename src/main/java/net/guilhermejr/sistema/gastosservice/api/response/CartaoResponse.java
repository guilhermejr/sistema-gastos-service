package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.time.LocalDate;

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
    /** Id do cartão no banco, quando ligado. */
    private String bancoContaId;
    /** Vencimento da primeira fatura buscada no banco. */
    private LocalDate bancoInicioFatura;

}
