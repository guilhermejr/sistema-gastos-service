package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransacaoBancoRepository extends JpaRepository<TransacaoBanco, Long> {

    List<TransacaoBanco> findAllByCartao(Cartao cartao);

    List<TransacaoBanco> findAllByCartaoAndFaturaBetweenOrderByDataDescIdDesc(Cartao cartao, LocalDate inicio, LocalDate fim);

}
