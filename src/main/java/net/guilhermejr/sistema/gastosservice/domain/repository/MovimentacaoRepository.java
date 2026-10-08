package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Movimentacao> findByIdAndUsuario(Long id, UUID usuario);

    /** Pagamentos de uma fatura, pelo mês do vencimento (ver LancamentoRepository). */
    List<Movimentacao> findAllByCartaoAndFaturaBetweenOrderByDataAscIdAsc(Cartao cartao, LocalDate inicio, LocalDate fim);

    /** A fatura do mês já recebeu algum pagamento (mesmo parcial, enquanto não estornado). */
    boolean existsByCartaoAndFaturaBetween(Cartao cartao, LocalDate inicio, LocalDate fim);

    @Query("SELECT m FROM Movimentacao m WHERE m.usuario = :usuario AND (m.contaOrigem = :conta OR m.contaDestino = :conta) "
            + "AND m.data BETWEEN :inicio AND :fim ORDER BY m.data ASC, m.id ASC")
    List<Movimentacao> findDaContaNoPeriodo(@Param("usuario") UUID usuario, @Param("conta") Conta conta,
                                            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    List<Movimentacao> findAllByUsuarioAndTipoAndDataBetweenOrderByDataAscIdAsc(UUID usuario, TipoMovimentacao tipo,
                                                                              LocalDate inicio, LocalDate fim);

    /** [contaId, soma] do que entrou em cada conta. */
    @Query("SELECT m.contaDestino.id, SUM(m.valor) FROM Movimentacao m WHERE m.usuario = :usuario "
            + "AND m.contaDestino IS NOT NULL GROUP BY m.contaDestino.id")
    List<Object[]> somarEntradasPorConta(@Param("usuario") UUID usuario);

    /** [contaId, soma] do que saiu de cada conta. */
    @Query("SELECT m.contaOrigem.id, SUM(m.valor) FROM Movimentacao m WHERE m.usuario = :usuario "
            + "AND m.contaOrigem IS NOT NULL GROUP BY m.contaOrigem.id")
    List<Object[]> somarSaidasPorConta(@Param("usuario") UUID usuario);

}
