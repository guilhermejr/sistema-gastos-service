package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contas")
public class Conta extends Auditoria implements Serializable, Ordenavel {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Saldo no dia do cadastro. O saldo atual é calculado a partir dele. */
    @Column(nullable = false)
    private BigDecimal saldoInicial;

    /** Entra no "Saldo Geral". O "Saldo Total" soma todas as contas ativas. */
    @Column(nullable = false)
    private Boolean somaSaldoGeral;

    /** O dashboard mostra o saldo dela no fim do ciclo. Não muda a projeção do Saldo Geral. */
    @Column(nullable = false)
    private Boolean mostraSaldoPrevisto;

    @Column(nullable = false)
    private Boolean ativo;

    /** Posição nas listas, escolhida pelo usuário (1 = primeira). */
    @Column(nullable = false)
    private Integer ordem;

}
