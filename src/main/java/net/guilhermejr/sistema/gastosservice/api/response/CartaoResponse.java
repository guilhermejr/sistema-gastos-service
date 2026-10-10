package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    /** Última busca das transações no banco, em UTC. */
    private LocalDateTime bancoSincronizado;
    /** Limite total e disponível na última busca no banco. */
    private BigDecimal bancoLimite;
    private BigDecimal bancoLimiteDisponivel;

}
