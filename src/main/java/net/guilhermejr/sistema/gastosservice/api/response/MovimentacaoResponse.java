package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MovimentacaoResponse {

    private Long id;
    private TipoMovimentacao tipo;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private ContaResumidoResponse contaOrigem;
    private ContaResumidoResponse contaDestino;
    private CartaoResumidoResponse cartao;
    private LocalDate fatura;

}
