package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.MovimentacaoResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovimentacaoMapper extends ModelMapperConfig {

    public MovimentacaoResponse mapObject(Movimentacao movimentacao) {
        return this.mapObject(movimentacao, MovimentacaoResponse.class);
    }

    public List<MovimentacaoResponse> mapList(List<Movimentacao> movimentacaos) {
        return this.mapList(movimentacaos, MovimentacaoResponse.class);
    }

}
