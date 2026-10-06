package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CartaoRequest {

    @NotBlank
    @Size(max = 255)
    private String nome;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer diaVencimento;

    /** Quantos dias antes do vencimento a fatura fecha. */
    @NotNull
    @Min(1)
    @Max(28)
    private Integer diasFechamento;

    /** Conta padrão para pagar a fatura. */
    @NotNull
    private Long contaId;

}
