package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.ContaMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ContaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ContaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import net.guilhermejr.sistema.gastosservice.util.OrdemUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class ContaService {

    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final RecorrenciaRepository recorrenciaRepository;
    private final SaldoService saldoService;
    private final ContaMapper contaMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    public List<ContaResponse> retornar(boolean somenteAtivas) {

        UUID usuario = usuario();
        List<Conta> contas = somenteAtivas
                ? contaRepository.findAllByUsuarioAndAtivoTrueOrderByOrdemAscNomeAsc(usuario)
                : contaRepository.findAllByUsuarioOrderByOrdemAscNomeAsc(usuario);
        Map<Long, BigDecimal> saldos = saldoService.saldos(usuario, contas);
        return contas.stream().map(c -> contaMapper.mapObject(c, saldos.get(c.getId()))).toList();

    }

    public ContaResponse retornarUm(Long id) {

        Conta conta = contaDoUsuario(id);
        return contaMapper.mapObject(conta, saldoService.saldo(conta));

    }

    @Transactional
    public ContaResponse inserir(ContaRequest contaRequest) {

        UUID usuario = usuario();
        String nome = contaRequest.getNome().trim();
        verificaSeExisteConta(usuario, nome, null);

        Conta conta = new Conta();
        conta.setNome(nome);
        conta.setSaldoInicial(converteStringUtil.toBigDecimal(contaRequest.getSaldoInicial()));
        conta.setSomaSaldoGeral(contaRequest.getSomaSaldoGeral());
        conta.setMostraSaldoPrevisto(!Boolean.FALSE.equals(contaRequest.getMostraSaldoPrevisto()));
        conta.setAtivo(true);
        conta.setOrdem(contaRepository.maiorOrdem(usuario) + 1);
        conta.setUsuario(usuario);

        Conta contaSave = contaRepository.save(conta);
        return contaMapper.mapObject(contaSave, saldoService.saldo(contaSave));

    }

    @Transactional
    public ContaResponse atualizar(Long id, ContaRequest contaRequest) {

        Conta conta = contaDoUsuario(id);
        String nome = contaRequest.getNome().trim();
        verificaSeExisteConta(conta.getUsuario(), nome, id);

        conta.setNome(nome);
        conta.setSaldoInicial(converteStringUtil.toBigDecimal(contaRequest.getSaldoInicial()));
        conta.setSomaSaldoGeral(contaRequest.getSomaSaldoGeral());
        if (contaRequest.getMostraSaldoPrevisto() != null) {
            conta.setMostraSaldoPrevisto(contaRequest.getMostraSaldoPrevisto());
        }

        Conta contaSave = contaRepository.save(conta);
        return contaMapper.mapObject(contaSave, saldoService.saldo(contaSave));

    }

    /**
     * Uma conta só sai de uso vazia e sem nada que dependa dela: o saldo deixaria de
     * aparecer no Saldo Total sem ter ido para lugar nenhum, e cartão ou despesa fixa
     * continuariam lançando numa conta que não aparece mais.
     */
    @Transactional
    public ContaResponse desativar(Long id) {

        Conta conta = contaDoUsuario(id);
        BigDecimal saldo = saldoService.saldo(conta);

        if (saldo.signum() != 0) {
            log.error("Conta {} com saldo {} não desativada", id, saldo);
            throw new ExceptionDefault("A conta ainda tem saldo de " + converteStringUtil.formatarMoeda(saldo)
                    + ". Transfira ou saque antes de desativar.");
        }
        if (cartaoRepository.existsByContaAndAtivoTrue(conta)) {
            log.error("Conta {} é conta de pagamento de cartão ativo", id);
            throw new ExceptionDefault("A conta é a conta de pagamento de um cartão ativo. Troque a conta do cartão antes.");
        }
        if (recorrenciaRepository.existsByContaAndAtivoTrue(conta)) {
            log.error("Conta {} tem lançamentos fixos ativos", id);
            throw new ExceptionDefault("A conta tem despesas ou receitas fixas. Encerre-as antes de desativar.");
        }

        conta.setAtivo(false);
        return contaMapper.mapObject(contaRepository.save(conta), saldo);

    }

    @Transactional
    public ContaResponse ativar(Long id) {

        Conta conta = contaDoUsuario(id);
        conta.setAtivo(true);
        return contaMapper.mapObject(contaRepository.save(conta), saldoService.saldo(conta));

    }

    /**
     * Troca a conta de lugar com a vizinha de cima ({@code deslocamento} -1) ou de baixo
     * (+1), contando também as desativadas, e devolve todas na ordem nova.
     */
    @Transactional
    public List<ContaResponse> mover(Long id, int deslocamento) {

        List<Conta> contas = new ArrayList<>(contaRepository.findAllByUsuarioOrderByOrdemAscNomeAsc(usuario()));
        contaRepository.saveAll(OrdemUtil.mover(contas, contaDoUsuario(id).getId(), deslocamento));
        return retornar(false);

    }

    /** Conta do usuário e ativa — o que se exige para lançar ou movimentar nela. */
    public Conta contaAtivaDoUsuario(Long id) {

        Conta conta = contaDoUsuario(id);
        if (!conta.getAtivo()) {
            throw new ExceptionDefault("A conta " + conta.getNome() + " está desativada.");
        }
        return conta;

    }

    /**
     * Devolve a conta somente se ela for do usuário autenticado. Conta de outro usuário
     * responde "não encontrada" em vez de 403 — não revela que o id existe.
     */
    public Conta contaDoUsuario(Long id) {

        return contaRepository.findByIdAndUsuario(id, usuario())
                .orElseThrow(() -> new ExceptionNotFound("Conta não encontrada: " + id));

    }

    private void verificaSeExisteConta(UUID usuario, String nome, Long idAtual) {

        contaRepository.findByUsuarioAndNomeIgnoreCase(usuario, nome)
                .filter(c -> !c.getId().equals(idAtual))
                .ifPresent(c -> {
                    log.error("Conta já cadastrada {}", nome);
                    throw new ExceptionDefault("Conta já cadastrada.");
                });

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
