package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransacaoBancoResponse {

    private Long id;
    private TipoLancamento tipo;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private LocalDate dataCompra;
    private LocalDate fatura;
    private Integer parcela;
    private Integer totalParcelas;
    private Boolean pendente;
    private String finalCartao;
    private String categoriaBanco;
    private SituacaoTransacaoBanco situacao;

}
