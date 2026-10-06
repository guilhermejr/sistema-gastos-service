package net.guilhermejr.sistema.gastosservice.domain.enums;

/** Alcance de uma alteração ou exclusão em lançamento parcelado ou fixo. */
public enum Escopo {
    /** Só o lançamento informado. */
    UNICO,
    /** O informado e os seguintes ainda não realizados da mesma série. */
    SEGUINTES
}
