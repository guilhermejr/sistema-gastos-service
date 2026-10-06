package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LancamentoMapper extends ModelMapperConfig {

    /** A estratégia STRICT não achata recorrencia.id em recorrenciaId sozinha. */
    public LancamentoMapper() {

        this.modelMapper.createTypeMap(Lancamento.class, LancamentoResponse.class)
                .addMappings(mapper -> mapper.map(src -> src.getRecorrencia().getId(), LancamentoResponse::setRecorrenciaId));

    }

    public LancamentoResponse mapObject(Lancamento lancamento) {
        return this.mapObject(lancamento, LancamentoResponse.class);
    }

    public List<LancamentoResponse> mapList(List<Lancamento> lancamentos) {
        return this.mapList(lancamentos, LancamentoResponse.class);
    }

}
