package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma transação do cartão como o banco a informa (Open Finance, via Pluggy). Não mexe
 * em fatura nem em saldo: serve para conciliar com os lançamentos.
 *
 * <p>Cada parcela de uma compra parcelada é uma transação. {@link #fatura} é o
 * vencimento da fatura em que o banco a pôs, no dia de vencimento do cartão — o mesmo
 * critério de {@code lancamentos.fatura}, para as duas se encontrarem pelo mês.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transacoes_banco")
public class TransacaoBanco extends Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(optional = false)
    private Cartao cartao;

    /** Id da transação na Pluggy. */
    @Column(nullable = false)
    private String bancoId;

    /** D = compra, R = estorno ou pagamento. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private TipoLancamento tipo;

    @Column(nullable = false)
    private String descricao;

    /** Sempre positivo; o sinal está em {@link #tipo}. */
    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    /** Data da compra original, nas parcelas. */
    private LocalDate dataCompra;

    @Column(nullable = false)
    private LocalDate fatura;

    private Integer parcela;

    private Integer totalParcelas;

    /** Ainda não fechada pelo banco: valor e descrição podem mudar. */
    @Column(nullable = false)
    private Boolean pendente;

    private String finalCartao;

    private String categoriaBanco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoTransacaoBanco situacao;

}
