package net.guilhermejr.sistema.gastosservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/** Preferências do usuário. Sem linha no banco, valem os padrões de {@link #padrao()}. */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "configuracoes")
public class Configuracao extends Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Dia em que começa o ciclo mensal do dashboard e do relatório. */
    @Column(nullable = false)
    private Integer diaInicioCiclo;

    /** Conta já escolhida ao incluir uma despesa; no máximo uma das duas (conta ou cartão). */
    @ManyToOne
    private Conta despesaConta;

    /** Cartão já escolhido ao incluir uma despesa. */
    @ManyToOne
    private Cartao despesaCartao;

    public static Configuracao padrao() {
        Configuracao configuracao = new Configuracao();
        configuracao.setDiaInicioCiclo(1);
        return configuracao;
    }

}
