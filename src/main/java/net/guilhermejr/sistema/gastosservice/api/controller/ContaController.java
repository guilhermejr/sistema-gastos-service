package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConfirmarConciliacaoContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ContaBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CriarDoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConciliacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.MovimentacaoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.service.ConciliacaoContaService;
import net.guilhermejr.sistema.gastosservice.service.ContaBancoService;
import net.guilhermejr.sistema.gastosservice.service.ContaService;
import net.guilhermejr.sistema.gastosservice.service.MovimentacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/contas")
public class ContaController {

    private final ContaService contaService;
    private final MovimentacaoService movimentacaoService;
    private final ContaBancoService contaBancoService;
    private final ConciliacaoContaService conciliacaoContaService;

    @GetMapping
    public ResponseEntity<List<ContaResponse>> retornar(@RequestParam(defaultValue = "false") boolean ativas) {

        log.info("Retornando contas (somente ativas: {})", ativas);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.retornar(ativas));

    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaResponse> retornarUm(@PathVariable Long id) {

        log.info("Recuperando uma conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.retornarUm(id));

    }

    @GetMapping("/{id}/movimentacoes")
    public ResponseEntity<List<MovimentacaoResponse>> movimentacoes(@PathVariable Long id, @RequestParam Integer ano, @RequestParam Integer mes) {

        log.info("Retornando movimentações da conta {} em {}/{}", id, mes, ano);
        return ResponseEntity.status(HttpStatus.OK).body(movimentacaoService.retornarDaConta(id, ano, mes));

    }

    @PostMapping
    public ResponseEntity<ContaResponse> inserir(@Valid @RequestBody ContaRequest contaRequest) {

        log.info("Inserindo conta");
        return ResponseEntity.status(HttpStatus.CREATED).body(contaService.inserir(contaRequest));

    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody ContaRequest contaRequest) {

        log.info("Atualizando conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.atualizar(id, contaRequest));

    }

    @PutMapping("/{id}/desativar")
    public ResponseEntity<ContaResponse> desativar(@PathVariable Long id) {

        log.info("Desativando conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.desativar(id));

    }

    @PutMapping("/{id}/subir")
    public ResponseEntity<List<ContaResponse>> subir(@PathVariable Long id) {

        log.info("Subindo conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.mover(id, -1));

    }

    @PutMapping("/{id}/descer")
    public ResponseEntity<List<ContaResponse>> descer(@PathVariable Long id) {

        log.info("Descendo conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.mover(id, 1));

    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<ContaResponse> ativar(@PathVariable Long id) {

        log.info("Ativando conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.ativar(id));

    }

    @PutMapping("/{id}/banco")
    public ResponseEntity<ContaResponse> ligarAoBanco(@PathVariable Long id, @Valid @RequestBody ContaBancoRequest contaBancoRequest) {

        log.info("Ligando conta {} ao banco", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaBancoService.ligar(id, contaBancoRequest));

    }

    @PostMapping("/{id}/banco/sincronizar")
    public ResponseEntity<SincronizacaoContaResponse> sincronizarComBanco(@PathVariable Long id) {

        log.info("Buscando no banco as transações da conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaBancoService.sincronizar(id));

    }

    @GetMapping("/{id}/banco/{ano}/{mes}/conciliacao")
    public ResponseEntity<ConciliacaoContaResponse> conciliacao(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes) {

        log.info("Conciliando com o banco o mês {}/{} da conta: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.conciliacao(id, ano, mes));

    }

    @PostMapping("/{id}/banco/{ano}/{mes}/conciliacao/confirmar")
    public ResponseEntity<ConciliacaoContaResponse> confirmarConciliacao(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes,
                                                                         @Valid @RequestBody ConfirmarConciliacaoContaRequest confirmarConciliacaoContaRequest) {

        log.info("Confirmando conciliação do mês {}/{} da conta: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.confirmar(id, ano, mes, confirmarConciliacaoContaRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/conciliar")
    public ResponseEntity<ConciliacaoContaResponse> conciliarTransacao(@PathVariable Long id, @PathVariable Long transacaoId,
                                                                       @Valid @RequestBody ConciliarContaRequest conciliarContaRequest) {

        log.info("Conciliando transação do banco {} da conta: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.conciliar(id, transacaoId, conciliarContaRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/criar")
    public ResponseEntity<ConciliacaoContaResponse> criarDaTransacao(@PathVariable Long id, @PathVariable Long transacaoId,
                                                                     @Valid @RequestBody CriarDoBancoRequest criarDoBancoRequest) {

        log.info("Criando lançamento a partir da transação do banco {} da conta: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.criar(id, transacaoId, criarDoBancoRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/ignorar")
    public ResponseEntity<ConciliacaoContaResponse> ignorarTransacao(@PathVariable Long id, @PathVariable Long transacaoId) {

        log.info("Ignorando transação do banco {} da conta: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.ignorar(id, transacaoId));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/desfazer")
    public ResponseEntity<ConciliacaoContaResponse> desfazerTransacao(@PathVariable Long id, @PathVariable Long transacaoId) {

        log.info("Desfazendo revisão da transação do banco {} da conta: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoContaService.desfazer(id, transacaoId));

    }

}
