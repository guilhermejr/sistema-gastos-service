package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cartoes")
public class Cartao extends Auditoria implements Serializable, Ordenavel {

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

    /** Posição nas listas, escolhida pelo usuário (1 = primeiro). */
    @Column(nullable = false)
    private Integer ordem;

    /** Id do cartão no banco (conta de crédito na Pluggy), quando ligado. */
    private String bancoContaId;

    /** Vencimento da primeira fatura cujas transações são buscadas no banco. */
    private LocalDate bancoInicioFatura;

    /** Última busca das transações no banco, em UTC. */
    private LocalDateTime bancoSincronizado;

    /** Limite total na última busca no banco. */
    private BigDecimal bancoLimite;

    /** Limite disponível na última busca no banco; o utilizado é a diferença. */
    private BigDecimal bancoLimiteDisponivel;

}
