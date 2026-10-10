package net.guilhermejr.sistema.gastosservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Lançamento novo a partir de uma transação do banco: o resto vem dela. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CriarDoBancoRequest {

    @NotBlank
    @Size(max = 255)
    private String descricao;

    @NotNull
    private Long categoriaId;

}
