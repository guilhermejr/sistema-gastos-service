package net.guilhermejr.sistema.gastosservice.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConciliacaoServiceTest {

    @Test
    @DisplayName("A descrição da compra é a da parcela sem o n/N do fim")
    public void descricao_sem_parcela() {

        Assertions.assertEquals("HNA*OBOTICARIOLAUR", ConciliacaoService.semParcela("HNA*OBOTICARIOLAUR02/10"));
        Assertions.assertEquals("SHEIN  *SHEIN.COMV", ConciliacaoService.semParcela("SHEIN  *SHEIN.COMV03/03"));
        Assertions.assertEquals("Panini Brasil Ltda", ConciliacaoService.semParcela("Panini Brasil Ltda 1/3"));
        Assertions.assertEquals("UBER TRIP", ConciliacaoService.semParcela("UBER TRIP"));

    }

}
