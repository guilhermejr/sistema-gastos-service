package net.guilhermejr.sistema.gastosservice.domain.enums;

/** Onde uma transação do banco está na conciliação com os lançamentos. */
public enum SituacaoTransacaoBanco {
    /** Chegou do banco e ninguém olhou ainda. */
    A_REVISAR,
    /** Ligada a um lançamento que já existia. */
    CONCILIADA,
    /** Virou um lançamento novo. */
    IMPORTADA,
    /** Não vira lançamento (ex.: o pagamento da fatura). */
    IGNORADA
}
