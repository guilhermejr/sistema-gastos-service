package net.guilhermejr.sistema.gastosservice.domain.enums;

/** O que a conciliação sugere fazer com uma transação do banco ainda a revisar. */
public enum SugestaoConciliacao {
    /** Casa com um lançamento só: confirmar liga os dois. */
    CONCILIAR,
    /** Mais de um lançamento casa (mesmo valor): o usuário escolhe. */
    ESCOLHER,
    /** Um lançamento próximo, com valor um pouco diferente: corrigir ou manter. */
    VALOR_DIFERENTE,
    /** Nenhum lançamento: criar um a partir do banco. */
    CRIAR,
    /** Não é compra (pagamento da fatura): ignorar. */
    IGNORAR
}
