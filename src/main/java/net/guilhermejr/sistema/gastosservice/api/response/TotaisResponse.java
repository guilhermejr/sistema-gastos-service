package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TotaisResponse {

    private BigDecimal realizado;
    private BigDecimal pendente;
    private BigDecimal total;

    public static TotaisResponse de(BigDecimal realizado, BigDecimal pendente) {
        return new TotaisResponse(realizado, pendente, realizado.add(pendente));
    }

}
