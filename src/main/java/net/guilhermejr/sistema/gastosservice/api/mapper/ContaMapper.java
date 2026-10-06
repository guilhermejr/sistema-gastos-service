package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.ContaResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ContaMapper extends ModelMapperConfig {

    /** O saldo não está na entidade: vem calculado do SaldoService. */
    public ContaResponse mapObject(Conta conta, BigDecimal saldo) {
        ContaResponse contaResponse = this.mapObject(conta, ContaResponse.class);
        contaResponse.setSaldo(saldo);
        return contaResponse;
    }

}
