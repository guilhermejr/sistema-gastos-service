package net.guilhermejr.sistema.gastosservice.domain.enums;

public enum StatusFatura {
    /** Ainda recebe compras. */
    ABERTA,
    /** Fechou, tem valor a pagar e o vencimento não passou. */
    FECHADA,
    /** Fechou, tem valor a pagar e o vencimento passou. */
    VENCIDA,
    /** Fechou e não há nada pendente. */
    PAGA
}
