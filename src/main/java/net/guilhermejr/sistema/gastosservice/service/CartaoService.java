package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.CartaoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.CartaoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaRepository recorrenciaRepository;
    private final ContaService contaService;
    private final CartaoMapper cartaoMapper;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    public List<CartaoResponse> retornar(boolean somenteAtivos) {

        UUID usuario = usuario();
        List<Cartao> cartoes = somenteAtivos
                ? cartaoRepository.findAllByUsuarioAndAtivoTrueOrderByNomeAsc(usuario)
                : cartaoRepository.findAllByUsuarioOrderByNomeAsc(usuario);
        return cartaoMapper.mapList(cartoes);

    }

    public CartaoResponse retornarUm(Long id) {

        return cartaoMapper.mapObject(cartaoDoUsuario(id));

    }

    @Transactional
    public CartaoResponse inserir(CartaoRequest cartaoRequest) {

        UUID usuario = usuario();
        String nome = cartaoRequest.getNome().trim();
        verificaSeExisteCartao(usuario, nome, null);

        Cartao cartao = new Cartao();
        cartao.setNome(nome);
        cartao.setDiaVencimento(cartaoRequest.getDiaVencimento());
        cartao.setDiasFechamento(cartaoRequest.getDiasFechamento());
        cartao.setConta(contaService.contaAtivaDoUsuario(cartaoRequest.getContaId()));
        cartao.setAtivo(true);
        cartao.setUsuario(usuario);

        return cartaoMapper.mapObject(cartaoRepository.save(cartao));

    }

    /**
     * Mudar vencimento ou fechamento muda a fatura das compras ainda não pagas, então
     * elas são reposicionadas. As já pagas ficam onde estavam.
     */
    @Transactional
    public CartaoResponse atualizar(Long id, CartaoRequest cartaoRequest) {

        Cartao cartao = cartaoDoUsuario(id);
        String nome = cartaoRequest.getNome().trim();
        verificaSeExisteCartao(cartao.getUsuario(), nome, id);

        Conta conta = Objects.equals(cartao.getConta().getId(), cartaoRequest.getContaId())
                ? cartao.getConta()
                : contaService.contaAtivaDoUsuario(cartaoRequest.getContaId());

        boolean mudouCiclo = !cartao.getDiaVencimento().equals(cartaoRequest.getDiaVencimento())
                || !cartao.getDiasFechamento().equals(cartaoRequest.getDiasFechamento());

        cartao.setNome(nome);
        cartao.setDiaVencimento(cartaoRequest.getDiaVencimento());
        cartao.setDiasFechamento(cartaoRequest.getDiasFechamento());
        cartao.setConta(conta);

        if (mudouCiclo) {
            List<Lancamento> pendentes = lancamentoRepository.findAllByCartaoAndRealizadoFalse(cartao);
            pendentes.forEach(l -> l.setFatura(faturaDaCompra(cartao, l)));
            lancamentoRepository.saveAll(pendentes);
            log.info("Cartão {}: {} lançamentos pendentes reposicionados nas faturas", id, pendentes.size());
        }

        return cartaoMapper.mapObject(cartaoRepository.save(cartao));

    }

    /**
     * Cartão desativado não recebe compras novas, mas as faturas já lançadas continuam
     * aparecendo para pagar. Despesa fixa no cartão precisa ser encerrada antes, senão
     * continuaria gerando compras nele.
     */
    @Transactional
    public CartaoResponse desativar(Long id) {

        Cartao cartao = cartaoDoUsuario(id);
        if (recorrenciaRepository.existsByCartaoAndAtivoTrue(cartao)) {
            log.error("Cartão {} tem lançamentos fixos ativos", id);
            throw new ExceptionDefault("O cartão tem despesas fixas. Encerre-as antes de desativar.");
        }
        cartao.setAtivo(false);
        return cartaoMapper.mapObject(cartaoRepository.save(cartao));

    }

    @Transactional
    public CartaoResponse ativar(Long id) {

        Cartao cartao = cartaoDoUsuario(id);
        if (!cartao.getConta().getAtivo()) {
            throw new ExceptionDefault("A conta de pagamento do cartão está desativada. Troque a conta antes.");
        }
        cartao.setAtivo(true);
        return cartaoMapper.mapObject(cartaoRepository.save(cartao));

    }

    /** Vencimento da fatura em que o lançamento entra, pelo ciclo atual do cartão. */
    public LocalDate faturaDaCompra(Cartao cartao, Lancamento lancamento) {
        return CalendarioUtil.vencimentoDaCompra(lancamento.getData(), cartao.getDiaVencimento(), cartao.getDiasFechamento());
    }

    /** Cartão do usuário e ativo — o que se exige para lançar nele. */
    public Cartao cartaoAtivoDoUsuario(Long id) {

        Cartao cartao = cartaoDoUsuario(id);
        if (!cartao.getAtivo()) {
            throw new ExceptionDefault("O cartão " + cartao.getNome() + " está desativado.");
        }
        return cartao;

    }

    /**
     * Devolve o cartão somente se ele for do usuário autenticado. Cartão de outro usuário
     * responde "não encontrado" em vez de 403 — não revela que o id existe.
     */
    public Cartao cartaoDoUsuario(Long id) {

        return cartaoRepository.findByIdAndUsuario(id, usuario())
                .orElseThrow(() -> new ExceptionNotFound("Cartão não encontrado: " + id));

    }

    private void verificaSeExisteCartao(UUID usuario, String nome, Long idAtual) {

        cartaoRepository.findByUsuarioAndNomeIgnoreCase(usuario, nome)
                .filter(c -> !c.getId().equals(idAtual))
                .ifPresent(c -> {
                    log.error("Cartão já cadastrado {}", nome);
                    throw new ExceptionDefault("Cartão já cadastrado.");
                });

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
