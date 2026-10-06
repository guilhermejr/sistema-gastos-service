package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cartoes")
public class Cartao extends Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private Integer diaVencimento;

    /** Quantos dias antes do vencimento a fatura fecha. */
    @Column(nullable = false)
    private Integer diasFechamento;

    /** Conta sugerida para pagar a fatura. */
    @ManyToOne(optional = false)
    private Conta conta;

    @Column(nullable = false)
    private Boolean ativo;

}
