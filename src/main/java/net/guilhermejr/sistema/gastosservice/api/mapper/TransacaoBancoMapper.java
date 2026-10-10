package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.TransacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransacaoBancoMapper extends ModelMapperConfig {

    public List<TransacaoBancoResponse> mapList(List<TransacaoBanco> transacoes) {
        return this.mapList(transacoes, TransacaoBancoResponse.class);
    }

}
