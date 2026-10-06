package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.CategoriaMapper;
import net.guilhermejr.sistema.gastosservice.api.request.CategoriaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CategoriaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CategoriaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaRepository recorrenciaRepository;
    private final CategoriaMapper categoriaMapper;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    public List<CategoriaResponse> retornar(TipoLancamento tipo) {

        UUID usuario = usuario();
        List<Categoria> categorias = tipo == null
                ? categoriaRepository.findAllByUsuarioOrderByTipoAscDescricaoAsc(usuario)
                : categoriaRepository.findAllByUsuarioAndTipoOrderByDescricaoAsc(usuario, tipo);
        return categoriaMapper.mapList(categorias);

    }

    public CategoriaResponse retornarUm(Long id) {

        return categoriaMapper.mapObject(categoriaDoUsuario(id));

    }

    @Transactional
    public CategoriaResponse inserir(CategoriaRequest categoriaRequest) {

        UUID usuario = usuario();
        String descricao = categoriaRequest.getDescricao().trim();
        verificaSeExisteCategoria(usuario, categoriaRequest.getTipo(), descricao, null);

        Categoria categoria = new Categoria();
        categoria.setDescricao(descricao);
        categoria.setTipo(categoriaRequest.getTipo());
        categoria.setAtivo(true);
        categoria.setUsuario(usuario);

        return categoriaMapper.mapObject(categoriaRepository.save(categoria));

    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest categoriaRequest) {

        Categoria categoria = categoriaDoUsuario(id);
        String descricao = categoriaRequest.getDescricao().trim();
        verificaSeExisteCategoria(categoria.getUsuario(), categoriaRequest.getTipo(), descricao, id);

        if (categoria.getTipo() != categoriaRequest.getTipo() && emUso(categoria)) {
            log.error("Categoria {} em uso não pode mudar de tipo", id);
            throw new ExceptionDefault("A categoria já tem lançamentos e não pode trocar entre despesa e receita.");
        }

        categoria.setDescricao(descricao);
        categoria.setTipo(categoriaRequest.getTipo());

        return categoriaMapper.mapObject(categoriaRepository.save(categoria));

    }

    @Transactional
    public CategoriaResponse alterarAtivo(Long id, boolean ativo) {

        Categoria categoria = categoriaDoUsuario(id);
        categoria.setAtivo(ativo);
        return categoriaMapper.mapObject(categoriaRepository.save(categoria));

    }

    /** Só apaga categoria sem uso; as usadas devem ser desativadas, para não perder o histórico. */
    @Transactional
    public void apagar(Long id) {

        Categoria categoria = categoriaDoUsuario(id);
        if (emUso(categoria)) {
            log.error("Categoria {} em uso não apagada", id);
            throw new ExceptionDefault("A categoria já tem lançamentos. Desative-a em vez de apagar.");
        }
        categoriaRepository.delete(categoria);

    }

    /** Categoria do usuário, ativa e do mesmo tipo do lançamento — o que se exige para lançar nela. */
    public Categoria categoriaParaLancamento(Long id, TipoLancamento tipo) {

        Categoria categoria = categoriaDoUsuario(id);
        if (!categoria.getAtivo()) {
            throw new ExceptionDefault("A categoria " + categoria.getDescricao() + " está desativada.");
        }
        if (categoria.getTipo() != tipo) {
            throw new ExceptionDefault("A categoria " + categoria.getDescricao() + " não é de "
                    + (tipo == TipoLancamento.D ? "despesa." : "receita."));
        }
        return categoria;

    }

    /**
     * Devolve a categoria somente se ela for do usuário autenticado. Categoria de outro
     * usuário responde "não encontrada" em vez de 403 — não revela que o id existe.
     */
    public Categoria categoriaDoUsuario(Long id) {

        return categoriaRepository.findByIdAndUsuario(id, usuario())
                .orElseThrow(() -> new ExceptionNotFound("Categoria não encontrada: " + id));

    }

    private boolean emUso(Categoria categoria) {
        return lancamentoRepository.existsByCategoria(categoria) || recorrenciaRepository.existsByCategoria(categoria);
    }

    private void verificaSeExisteCategoria(UUID usuario, TipoLancamento tipo, String descricao, Long idAtual) {

        categoriaRepository.findByUsuarioAndTipoAndDescricaoIgnoreCase(usuario, tipo, descricao)
                .filter(c -> !c.getId().equals(idAtual))
                .ifPresent(c -> {
                    log.error("Categoria já cadastrada {}", descricao);
                    throw new ExceptionDefault("Categoria já cadastrada.");
                });

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
