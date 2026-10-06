package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Molde de uma despesa ou receita fixa. As ocorrências são criadas em lancamentos sob
 * demanda, à medida que alguém consulta meses à frente; {@link #geradoAte} é a data da
 * última ocorrência criada.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recorrencias")
public class Recorrencia extends Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private TipoLancamento tipo;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private BigDecimal valor;

    /** Dia do mês, recuado para o último dia nos meses mais curtos. */
    @Column(nullable = false)
    private Integer dia;

    @ManyToOne(optional = false)
    private Categoria categoria;

    @ManyToOne
    private Conta conta;

    @ManyToOne
    private Cartao cartao;

    @Column(nullable = false)
    private LocalDate geradoAte;

    /** Falso quando a série foi encerrada; nada mais é gerado. */
    @Column(nullable = false)
    private Boolean ativo;

}
