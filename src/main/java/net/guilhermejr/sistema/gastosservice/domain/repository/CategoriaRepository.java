package net.guilhermejr.sistema.gastosservice.domain.repository;

import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Busca por id restringindo ao dono: id de outro usuário não é encontrado. */
    Optional<Categoria> findByIdAndUsuario(Long id, UUID usuario);

    List<Categoria> findAllByUsuarioOrderByTipoAscDescricaoAsc(UUID usuario);

    List<Categoria> findAllByUsuarioAndTipoOrderByDescricaoAsc(UUID usuario, TipoLancamento tipo);

    Optional<Categoria> findByUsuarioAndTipoAndDescricaoIgnoreCase(UUID usuario, TipoLancamento tipo, String descricao);

}
