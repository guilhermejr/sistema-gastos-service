package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Cartao> findByIdAndUsuario(Long id, UUID usuario);

    List<Cartao> findAllByUsuarioOrderByOrdemAscNomeAsc(UUID usuario);

    List<Cartao> findAllByUsuarioAndAtivoTrueOrderByOrdemAscNomeAsc(UUID usuario);

    @Query("SELECT COALESCE(MAX(c.ordem), 0) FROM Cartao c WHERE c.usuario = :usuario")
    int maiorOrdem(@Param("usuario") UUID usuario);

    Optional<Cartao> findByUsuarioAndNomeIgnoreCase(UUID usuario, String nome);

    boolean existsByContaAndAtivoTrue(Conta conta);

}
