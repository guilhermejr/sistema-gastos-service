package net.guilhermejr.sistema.gastosservice.domain.entity;

/** Cadastro que o usuário põe na ordem que quiser (contas e cartões). */
public interface Ordenavel {

    Long getId();

    Integer getOrdem();

    void setOrdem(Integer ordem);

}
