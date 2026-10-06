package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CategoriaRequest {

    @NotBlank
    @Size(max = 255)
    private String descricao;

    @NotNull
    private TipoLancamento tipo;

}
