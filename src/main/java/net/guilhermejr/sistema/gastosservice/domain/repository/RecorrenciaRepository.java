package net.guilhermejr.sistema.gastosservice.domain.repository;

import jakarta.persistence.LockModeType;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Recorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecorrenciaRepository extends JpaRepository<Recorrencia, Long> {

    /**
     * Séries com ocorrências por gerar. O lock de escrita serializa a geração: o
     * dashboard e o relatório podem pedir o mesmo mês ao mesmo tempo, e sem ele as
     * duas requisições criariam a mesma ocorrência.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recorrencia r WHERE r.usuario = :usuario AND r.ativo = true AND r.geradoAte < :ate")
    List<Recorrencia> findParaGerar(@Param("usuario") UUID usuario, @Param("ate") LocalDate ate);

    boolean existsByContaAndAtivoTrue(Conta conta);

    boolean existsByCategoria(Categoria categoria);

    boolean existsByCartaoAndAtivoTrue(Cartao cartao);

}
