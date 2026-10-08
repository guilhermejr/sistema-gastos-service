package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {

    Optional<Configuracao> findByUsuario(UUID usuario);

}
