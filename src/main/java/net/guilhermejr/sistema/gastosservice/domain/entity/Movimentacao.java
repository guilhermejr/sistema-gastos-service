package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tudo o que mexe no saldo de uma conta sem ser receita ou despesa.
 *
 * <ul>
 *   <li>DEPOSITO — só destino</li>
 *   <li>SAQUE — só origem</li>
 *   <li>TRANSFERENCIA — origem e destino</li>
 *   <li>PAGAMENTO_FATURA — origem, cartão e vencimento da fatura</li>
 * </ul>
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movimentacoes")
public class Movimentacao extends Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentacao tipo;

    private String descricao;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @ManyToOne
    private Conta contaOrigem;

    @ManyToOne
    private Conta contaDestino;

    @ManyToOne
    private Cartao cartao;

    private LocalDate fatura;

}
