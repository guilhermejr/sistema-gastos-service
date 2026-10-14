package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.ItemAgendaResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AvisoSemanalServiceTest {

    private static ItemAgendaResponse item(String descricao, String data, String valor, boolean atrasado) {
        return ItemAgendaResponse.builder().descricao(descricao).data(LocalDate.parse(data))
                .valor(new BigDecimal(valor)).atrasado(atrasado).build();
    }

    @Test
    @DisplayName("Nada a pagar nem a receber: não há aviso")
    public void vazio() {

        Assertions.assertEquals(Optional.empty(), AvisoSemanalService.mensagem(List.of(), List.of()));

    }

    @Test
    @DisplayName("Uma seção por lista, com total, data, valor e atrasados marcados")
    public void secoes() {

        String mensagem = AvisoSemanalService.mensagem(
                List.of(item("Fatura Itaú", "2026-10-10", "2300.00", true), item("Aluguel", "2026-10-15", "1500.00", false)),
                List.of(item("Salário", "2026-10-16", "8000.50", false))).orElseThrow();

        Assertions.assertEquals("""
                A pagar (R$ 3.800,00):
                • 10/10 Fatura Itaú: R$ 2.300,00 (atrasado)
                • 15/10 Aluguel: R$ 1.500,00

                A receber (R$ 8.000,50):
                • 16/10 Salário: R$ 8.000,50""", mensagem);

    }

    @Test
    @DisplayName("Só a pagar: a seção a receber não aparece")
    public void so_pagar() {

        String mensagem = AvisoSemanalService.mensagem(List.of(item("Luz", "2026-10-14", "180.00", false)), List.of()).orElseThrow();

        Assertions.assertEquals("A pagar (R$ 180,00):\n• 14/10 Luz: R$ 180,00", mensagem);

    }

    @Test
    @DisplayName("Acima do limite do Pushover: corta itens e diz quantos ficaram de fora, mantendo o total")
    public void limite() {

        List<ItemAgendaResponse> muitos = Collections.nCopies(60, item("Uma despesa com descrição comprida", "2026-10-14", "10.00", false));

        String mensagem = AvisoSemanalService.mensagem(muitos, List.of()).orElseThrow();

        Assertions.assertTrue(mensagem.length() <= AvisoSemanalService.LIMITE_MENSAGEM, String.valueOf(mensagem.length()));
        Assertions.assertTrue(mensagem.startsWith("A pagar (R$ 600,00):"));
        Assertions.assertTrue(mensagem.matches("(?s).*\n… e mais \\d+$"), mensagem);

    }

}
