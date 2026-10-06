package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.api.response.CategoriaResponse;
import net.guilhermejr.sistema.gastosservice.config.ModelMapperConfig;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoriaMapper extends ModelMapperConfig {

    public CategoriaResponse mapObject(Categoria categoria) {
        return this.mapObject(categoria, CategoriaResponse.class);
    }

    public List<CategoriaResponse> mapList(List<Categoria> categorias) {
        return this.mapList(categorias, CategoriaResponse.class);
    }

}
