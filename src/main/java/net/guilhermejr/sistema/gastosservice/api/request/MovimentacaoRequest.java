package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.DataBrasil;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.ValorMonetario;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;

/**
 * Depósito (só destino), saque (só origem) ou transferência (as duas). Pagamento de
 * fatura tem endpoint próprio, no cartão.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MovimentacaoRequest {

    @NotNull
    private TipoMovimentacao tipo;

    private Long contaOrigemId;

    private Long contaDestinoId;

    @NotBlank
    @ValorMonetario
    private String valor;

    @NotBlank
    @DataBrasil
    private String data;

    @Size(max = 255)
    private String descricao;

}
