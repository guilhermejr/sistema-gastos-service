package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.DataBrasil;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.ValorMonetario;
import net.guilhermejr.sistema.gastosservice.domain.enums.Repeticao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LancamentoRequest {

    @NotNull
    private TipoLancamento tipo;

    @NotBlank
    @Size(max = 255)
    private String descricao;

    /** Em lançamento parcelado, é o valor total da compra. */
    @NotBlank
    @ValorMonetario
    private String valor;

    @NotBlank
    @DataBrasil
    private String data;

    @NotNull
    private Long categoriaId;

    /** Informe conta ou cartão, nunca os dois. */
    private Long contaId;

    private Long cartaoId;

    /** Ignorado em cartão: lá quem realiza é o pagamento da fatura. */
    @Builder.Default
    private Boolean realizado = false;

    /** Só na inclusão; na alteração a série não muda de natureza. */
    @Builder.Default
    private Repeticao repeticao = Repeticao.UNICA;

    /** Obrigatório quando a repetição é PARCELADA. */
    @Min(2)
    @Max(120)
    private Integer parcelas;

    /**
     * Só na inclusão em cartão: a fatura escolhida, quando não é a da data da compra.
     * Numa compra parcelada, todas as parcelas andam os mesmos meses; numa fixa, só a
     * primeira ocorrência.
     */
    @Valid
    private FaturaDestinoRequest fatura;

}
