package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saldo de conta = saldo inicial
 *   + receitas realizadas − despesas realizadas na conta
 *   + depósitos e transferências recebidas
 *   − saques, transferências enviadas e pagamentos de fatura.
 *
 * <p>Lançamento pendente não conta, e lançamento de cartão nunca conta: ele chega à
 * conta pelo pagamento da fatura. Movimentações valem a partir do registro, mesmo com
 * data futura.
 */
@RequiredArgsConstructor
@Service
public class SaldoService {

    private final LancamentoRepository lancamentoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    /** Saldo de cada conta informada, por id. Três consultas, qualquer que seja o número de contas. */
    public Map<Long, BigDecimal> saldos(UUID usuario, List<Conta> contas) {

        Map<Long, BigDecimal> variacao = new HashMap<>();

        for (Object[] linha : lancamentoRepository.somarRealizadosPorConta(usuario)) {
            BigDecimal soma = (BigDecimal) linha[2];
            variacao.merge((Long) linha[0], linha[1] == TipoLancamento.R ? soma : soma.negate(), BigDecimal::add);
        }
        for (Object[] linha : movimentacaoRepository.somarEntradasPorConta(usuario)) {
            variacao.merge((Long) linha[0], (BigDecimal) linha[1], BigDecimal::add);
        }
        for (Object[] linha : movimentacaoRepository.somarSaidasPorConta(usuario)) {
            variacao.merge((Long) linha[0], ((BigDecimal) linha[1]).negate(), BigDecimal::add);
        }

        Map<Long, BigDecimal> saldos = new HashMap<>();
        contas.forEach(conta -> saldos.put(conta.getId(),
                conta.getSaldoInicial().add(variacao.getOrDefault(conta.getId(), BigDecimal.ZERO))));
        return saldos;

    }

    public BigDecimal saldo(Conta conta) {
        return saldos(conta.getUsuario(), List.of(conta)).get(conta.getId());
    }

}
