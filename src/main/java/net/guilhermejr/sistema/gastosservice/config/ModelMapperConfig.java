package net.guilhermejr.sistema.gastosservice.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.util.List;
import java.util.stream.Collectors;

public class ModelMapperConfig {

    protected ModelMapper modelMapper;

    /**
     * Estratégia STRICT: as respostas têm vários objetos aninhados com campos de mesmo
     * nome (conta.nome, cartao.nome, cartao.conta.nome), e a estratégia padrão escolhe
     * entre eles por aproximação.
     */
    public ModelMapperConfig() {
        this.modelMapper = new ModelMapper();
        this.modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    }

    public <S, T> T mapObject(S source, Class<T> targetClass) {
        return this.modelMapper.map(source, targetClass);
    }

    public <S, T> List<T> mapList(List<S> source, Class<T> targetClass) {
        return source
                .stream()
                .map(element -> modelMapper.map(element, targetClass))
                .collect(Collectors.toList());
    }

}
