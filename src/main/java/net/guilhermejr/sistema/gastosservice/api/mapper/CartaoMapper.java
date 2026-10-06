package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.CartaoResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CartaoMapper extends ModelMapperConfig {

    public CartaoResponse mapObject(Cartao cartao) {
        return this.mapObject(cartao, CartaoResponse.class);
    }

    public List<CartaoResponse> mapList(List<Cartao> cartaos) {
        return this.mapList(cartaos, CartaoResponse.class);
    }

}
