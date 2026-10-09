package net.guilhermejr.sistema.gastosservice.util;

import net.guilhermejr.sistema.gastosservice.domain.entity.Ordenavel;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;

import java.util.Collections;
import java.util.List;

public final class OrdemUtil {

    private OrdemUtil() {
    }

    /**
     * Troca o item com o vizinho de cima ({@code deslocamento} -1) ou de baixo (+1) e
     * renumera todos de 1 em diante — assim itens com a mesma ordem (empatados por nome)
     * também se separam. A lista vem na ordem atual e é alterada no lugar.
     */
    public static <T extends Ordenavel> List<T> mover(List<T> itens, Long id, int deslocamento) {

        int origem = -1;
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).getId().equals(id)) origem = i;
        }
        int destino = origem + deslocamento;
        if (origem < 0 || destino < 0 || destino >= itens.size()) {
            throw new ExceptionDefault(deslocamento < 0 ? "Já está no início da lista." : "Já está no fim da lista.");
        }

        Collections.swap(itens, origem, destino);
        for (int i = 0; i < itens.size(); i++) {
            itens.get(i).setOrdem(i + 1);
        }
        return itens;

    }

}
