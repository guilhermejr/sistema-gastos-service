package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.TransacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransacaoBancoMapper extends ModelMapperConfig {

    /** A estratégia STRICT não achata lancamento.id em lancamentoId sozinha. */
    public TransacaoBancoMapper() {

        this.modelMapper.createTypeMap(TransacaoBanco.class, TransacaoBancoResponse.class)
                .addMappings(mapper -> mapper.map(src -> src.getLancamento().getId(), TransacaoBancoResponse::setLancamentoId));

    }

    public TransacaoBancoResponse mapObject(TransacaoBanco transacao) {
        return this.mapObject(transacao, TransacaoBancoResponse.class);
    }

    public List<TransacaoBancoResponse> mapList(List<TransacaoBanco> transacoes) {
        return this.mapList(transacoes, TransacaoBancoResponse.class);
    }

}
