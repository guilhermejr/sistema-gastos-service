package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TransacaoBancoRepository extends JpaRepository<TransacaoBanco, Long> {

    List<TransacaoBanco> findAllByCartao(Cartao cartao);

    Optional<TransacaoBanco> findByIdAndCartao(Long id, Cartao cartao);

    List<TransacaoBanco> findAllByConta(Conta conta);

    Optional<TransacaoBanco> findByIdAndConta(Long id, Conta conta);

    List<TransacaoBanco> findAllByContaAndDataBetweenOrderByDataDescIdDesc(Conta conta, LocalDate inicio, LocalDate fim);

    boolean existsByMovimentacao(Movimentacao movimentacao);

    /** Lançamentos da conta já ligados a uma transação do banco. */
    @Query("SELECT t.lancamento.id FROM TransacaoBanco t WHERE t.conta = :conta AND t.lancamento IS NOT NULL")
    Set<Long> lancamentosLigados(@Param("conta") Conta conta);

    /** Movimentações já ligadas a uma transação do banco da conta. */
    @Query("SELECT t.movimentacao.id FROM TransacaoBanco t WHERE t.conta = :conta AND t.movimentacao IS NOT NULL")
    Set<Long> movimentacoesLigadas(@Param("conta") Conta conta);

    boolean existsByLancamento(Lancamento lancamento);

    /** A última transação com esta categoria do banco que virou lançamento: a categoria dele é a sugestão. */
    Optional<TransacaoBanco> findFirstByUsuarioAndCategoriaBancoAndLancamentoNotNullOrderByAtualizadoDesc(UUID usuario, String categoriaBanco);

    List<TransacaoBanco> findAllByCartaoAndFaturaBetweenOrderByDataDescIdDesc(Cartao cartao, LocalDate inicio, LocalDate fim);

    /** Lançamentos do cartão já ligados a uma transação do banco, de qualquer fatura. */
    @Query("SELECT t.lancamento.id FROM TransacaoBanco t WHERE t.cartao = :cartao AND t.lancamento IS NOT NULL")
    Set<Long> lancamentosLigados(@Param("cartao") Cartao cartao);

}
