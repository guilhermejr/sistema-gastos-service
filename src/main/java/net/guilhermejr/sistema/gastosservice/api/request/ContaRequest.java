package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.ValorMonetario;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ContaRequest {

    @NotBlank
    @Size(max = 255)
    private String nome;

    @NotBlank
    @ValorMonetario(negativo = true)
    private String saldoInicial;

    @NotNull
    private Boolean somaSaldoGeral;

}
