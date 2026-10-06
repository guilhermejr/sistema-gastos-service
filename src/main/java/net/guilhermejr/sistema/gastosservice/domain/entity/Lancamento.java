package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma receita ou despesa, numa conta ou num cartão — nunca nos dois.
 *
 * <p>Em conta, {@link #realizado} é marcado pelo usuário e só o que está realizado
 * conta no saldo. Em cartão, o lançamento não mexe em conta nenhuma: quem mexe é o
 * pagamento da fatura, e é ele que marca os lançamentos como realizados.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lancamentos")
public class Lancamento extends Auditoria implements Serializable {

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

    @Column(nullable = false)
    private LocalDate data;

    @ManyToOne(optional = false)
    private Categoria categoria;

    @ManyToOne
    private Conta conta;

    @ManyToOne
    private Cartao cartao;

    /** Vencimento da fatura em que o lançamento entra. Só em cartão. */
    private LocalDate fatura;

    /**
     * A compra foi transferida à mão para uma fatura posterior à da sua data. Ver
     * CartaoService.posicionarNaFatura, que respeita a escolha.
     */
    @Column(nullable = false)
    private Boolean faturaTransferida = false;

    @Column(nullable = false)
    private Boolean realizado;

    private Integer parcela;

    private Integer totalParcelas;

    /** Identifica as parcelas de uma mesma compra. */
    private UUID parcelamento;

    @ManyToOne
    private Recorrencia recorrencia;

    /** Pagamento de fatura que quitou este lançamento de cartão. */
    @ManyToOne
    private Movimentacao pagamento;

}
