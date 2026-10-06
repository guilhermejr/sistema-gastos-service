package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Campos comuns a todas as tabelas. Datas em UTC, como nos demais serviços. */
@Getter
@Setter
@MappedSuperclass
public abstract class Auditoria {

    @Column(updatable = false)
    private LocalDateTime criado;

    private LocalDateTime atualizado;

    @Column(nullable = false, updatable = false)
    private UUID usuario;

    @PrePersist
    void aoInserir() {
        criado = LocalDateTime.now(ZoneOffset.UTC);
        atualizado = criado;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizado = LocalDateTime.now(ZoneOffset.UTC);
    }

}
