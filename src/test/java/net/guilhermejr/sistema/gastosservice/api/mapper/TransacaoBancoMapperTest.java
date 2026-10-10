package net.guilhermejr.sistema.gastosservice.api.mapper;

import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransacaoBancoMapperTest {

    private final TransacaoBancoMapper mapper = new TransacaoBancoMapper();

    @Test
    void lancamentoLigadoViraLancamentoId() {
        Lancamento lancamento = new Lancamento();
        lancamento.setId(7L);
        TransacaoBanco ligada = new TransacaoBanco();
        ligada.setLancamento(lancamento);

        assertThat(mapper.mapObject(ligada).getLancamentoId()).isEqualTo(7L);
        assertThat(mapper.mapObject(new TransacaoBanco()).getLancamentoId()).isNull();
    }

}
