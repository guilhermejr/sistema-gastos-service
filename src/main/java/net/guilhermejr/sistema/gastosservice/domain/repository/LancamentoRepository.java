package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Recorrencia;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Lancamento> findByIdAndUsuario(Long id, UUID usuario);

    List<Lancamento> findAllByUsuarioAndDataBetweenOrderByDataAscIdAsc(UUID usuario, LocalDate inicio, LocalDate fim);

    /**
     * Lançamentos de uma fatura. A busca é pelo mês do vencimento, não pelo dia: se o
     * dia de vencimento do cartão mudar, o que já foi pago continua gravado com a data
     * antiga e precisa continuar aparecendo na mesma fatura.
     */
    List<Lancamento> findAllByCartaoAndFaturaBetweenOrderByDataAscIdAsc(Cartao cartao, LocalDate inicio, LocalDate fim);

    boolean existsByCategoria(Categoria categoria);

    /** Compras de cartão do usuário cujas faturas vencem no período, de qualquer cartão. */
    List<Lancamento> findAllByUsuarioAndCartaoIsNotNullAndFaturaBetween(UUID usuario, LocalDate inicio, LocalDate fim);

    List<Lancamento> findAllByPagamento(Movimentacao pagamento);

    List<Lancamento> findAllByParcelamentoAndParcelaGreaterThanEqualAndRealizadoFalse(UUID parcelamento, Integer parcela);

    List<Lancamento> findAllByRecorrenciaAndDataGreaterThanEqualAndRealizadoFalse(Recorrencia recorrencia, LocalDate data);

    List<Lancamento> findAllByCartaoAndRealizadoFalse(Cartao cartao);

    /** [contaId, tipo, soma] do que já foi realizado em cada conta. */
    @Query("SELECT l.conta.id, l.tipo, SUM(l.valor) FROM Lancamento l WHERE l.usuario = :usuario "
            + "AND l.conta IS NOT NULL AND l.realizado = true GROUP BY l.conta.id, l.tipo")
    List<Object[]> somarRealizadosPorConta(@Param("usuario") UUID usuario);

    /**
     * Pendentes em conta, do mais antigo para o mais novo — atrasados primeiro, porque
     * continuam por pagar ou receber.
     */
    @Query("SELECT l FROM Lancamento l WHERE l.usuario = :usuario AND l.tipo = :tipo "
            + "AND l.conta IS NOT NULL AND l.realizado = false ORDER BY l.data ASC, l.id ASC")
    List<Lancamento> findPendentesEmConta(@Param("usuario") UUID usuario, @Param("tipo") TipoLancamento tipo, Pageable pageable);

    /** Tudo o que ainda não foi pago em cartão, para montar as faturas em aberto. */
    @Query("SELECT l FROM Lancamento l WHERE l.usuario = :usuario AND l.cartao IS NOT NULL AND l.realizado = false")
    List<Lancamento> findPendentesEmCartao(@Param("usuario") UUID usuario);

}
