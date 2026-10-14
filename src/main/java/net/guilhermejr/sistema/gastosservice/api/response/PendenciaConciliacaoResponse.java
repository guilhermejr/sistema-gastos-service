package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

/**
 * Transações do banco que já dá para conciliar: no cartão, até a fatura atual (as parcelas
 * futuras esperam a fatura delas); na conta, até hoje. {@code ano}/{@code mes} é o mês mais
 * antigo com pendência — a fatura, no cartão; o extrato, na conta —, onde a conciliação abre.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PendenciaConciliacaoResponse {

    private int quantidade;
    private int ano;
    private int mes;

}
