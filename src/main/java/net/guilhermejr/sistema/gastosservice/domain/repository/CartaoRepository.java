package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Cartao> findByIdAndUsuario(Long id, UUID usuario);

    List<Cartao> findAllByUsuarioOrderByNomeAsc(UUID usuario);

    List<Cartao> findAllByUsuarioAndAtivoTrueOrderByNomeAsc(UUID usuario);

    Optional<Cartao> findByUsuarioAndNomeIgnoreCase(UUID usuario, String nome);

    boolean existsByContaAndAtivoTrue(Conta conta);

}
