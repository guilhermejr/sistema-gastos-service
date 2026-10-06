package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Conta> findByIdAndUsuario(Long id, UUID usuario);

    List<Conta> findAllByUsuarioOrderByNomeAsc(UUID usuario);

    List<Conta> findAllByUsuarioAndAtivoTrueOrderByNomeAsc(UUID usuario);

    Optional<Conta> findByUsuarioAndNomeIgnoreCase(UUID usuario, String nome);

}
