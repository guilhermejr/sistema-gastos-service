package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ContaServiceTest {

    /** Contas 1, 2 e 3, com as ordens dadas (empates vêm na ordem da lista, como do banco). */
    private static List<Conta> contas(int... ordens) {

        List<Conta> contas = new ArrayList<>();
        for (int i = 0; i < ordens.length; i++) {
            Conta conta = new Conta();
            conta.setId(i + 1L);
            conta.setOrdem(ordens[i]);
            contas.add(conta);
        }
        return contas;

    }

    private static List<Long> ids(List<Conta> contas) {
        return contas.stream().map(Conta::getId).toList();
    }

    @Test
    @DisplayName("Sobe a conta trocando com a de cima")
    public void sobe() {

        List<Conta> resultado = ContaService.reordenar(contas(1, 2, 3), 3L, -1);

        Assertions.assertEquals(List.of(1L, 3L, 2L), ids(resultado));
        Assertions.assertEquals(List.of(1, 2, 3), resultado.stream().map(Conta::getOrdem).toList());

    }

    @Test
    @DisplayName("Desce a conta trocando com a de baixo")
    public void desce() {

        Assertions.assertEquals(List.of(2L, 1L, 3L), ids(ContaService.reordenar(contas(1, 2, 3), 1L, 1)));

    }

    @Test
    @DisplayName("Renumera contas empatadas na ordem")
    public void renumera_empatadas() {

        List<Conta> resultado = ContaService.reordenar(contas(1, 1, 1), 2L, 1);

        Assertions.assertEquals(List.of(1L, 3L, 2L), ids(resultado));
        Assertions.assertEquals(List.of(1, 2, 3), resultado.stream().map(Conta::getOrdem).toList());

    }

    @Test
    @DisplayName("Recusa subir a primeira e descer a última")
    public void recusa_nas_pontas() {

        Assertions.assertThrows(ExceptionDefault.class, () -> ContaService.reordenar(contas(1, 2, 3), 1L, -1));
        Assertions.assertThrows(ExceptionDefault.class, () -> ContaService.reordenar(contas(1, 2, 3), 3L, 1));

    }

}
