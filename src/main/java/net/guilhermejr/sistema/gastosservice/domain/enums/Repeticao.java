package net.guilhermejr.sistema.gastosservice.domain.enums;

public enum Repeticao {
    /** Lançamento avulso. */
    UNICA,
    /** Despesa/receita fixa: repete todo mês no mesmo dia, sem data para acabar. */
    FIXA,
    /** O valor informado é o total, dividido em parcelas mensais. */
    PARCELADA
}
