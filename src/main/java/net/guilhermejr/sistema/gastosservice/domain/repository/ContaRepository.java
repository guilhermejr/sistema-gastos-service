package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Conta> findByIdAndUsuario(Long id, UUID usuario);

    List<Conta> findAllByUsuarioOrderByOrdemAscNomeAsc(UUID usuario);

    List<Conta> findAllByUsuarioAndAtivoTrueOrderByOrdemAscNomeAsc(UUID usuario);

    @Query("SELECT COALESCE(MAX(c.ordem), 0) FROM Conta c WHERE c.usuario = :usuario")
    int maiorOrdem(@Param("usuario") UUID usuario);

    Optional<Conta> findByUsuarioAndNomeIgnoreCase(UUID usuario, String nome);

}
