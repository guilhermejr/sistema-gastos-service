package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.CartaoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CartaoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConfirmarConciliacaoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CriarDoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.PagamentoFaturaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ConciliacaoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.TransacaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.service.BancoService;
import net.guilhermejr.sistema.gastosservice.service.CartaoService;
import net.guilhermejr.sistema.gastosservice.service.ConciliacaoService;
import net.guilhermejr.sistema.gastosservice.service.FaturaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/cartoes")
public class CartaoController {

    private final CartaoService cartaoService;
    private final FaturaService faturaService;
    private final BancoService bancoService;
    private final ConciliacaoService conciliacaoService;

    @GetMapping
    public ResponseEntity<List<CartaoResponse>> retornar(@RequestParam(defaultValue = "false") boolean ativos) {

        log.info("Retornando cartões (somente ativos: {})", ativos);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.retornar(ativos));

    }

    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponse> retornarUm(@PathVariable Long id) {

        log.info("Recuperando um cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.retornarUm(id));

    }

    @PostMapping
    public ResponseEntity<CartaoResponse> inserir(@Valid @RequestBody CartaoRequest cartaoRequest) {

        log.info("Inserindo cartão");
        return ResponseEntity.status(HttpStatus.CREATED).body(cartaoService.inserir(cartaoRequest));

    }

    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponse> atualizar(@PathVariable Long id, @Valid @RequestBody CartaoRequest cartaoRequest) {

        log.info("Atualizando cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.atualizar(id, cartaoRequest));

    }

    @PutMapping("/{id}/desativar")
    public ResponseEntity<CartaoResponse> desativar(@PathVariable Long id) {

        log.info("Desativando cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.desativar(id));

    }

    @PutMapping("/{id}/subir")
    public ResponseEntity<List<CartaoResponse>> subir(@PathVariable Long id) {

        log.info("Subindo cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.mover(id, -1));

    }

    @PutMapping("/{id}/descer")
    public ResponseEntity<List<CartaoResponse>> descer(@PathVariable Long id) {

        log.info("Descendo cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.mover(id, 1));

    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<CartaoResponse> ativar(@PathVariable Long id) {

        log.info("Ativando cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.ativar(id));

    }

    @GetMapping("/{id}/faturas/atual")
    public ResponseEntity<FaturaResponse> faturaAtual(@PathVariable Long id) {

        log.info("Retornando fatura atual do cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(faturaService.atual(id));

    }

    @GetMapping("/{id}/faturas/{ano}/{mes}")
    public ResponseEntity<FaturaResponse> fatura(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes) {

        log.info("Retornando fatura {}/{} do cartão: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(faturaService.retornar(id, ano, mes));

    }

    @PostMapping("/{id}/faturas/{ano}/{mes}/pagamento")
    public ResponseEntity<FaturaResponse> pagar(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes,
                                                @Valid @RequestBody PagamentoFaturaRequest pagamentoFaturaRequest) {

        log.info("Pagando fatura {}/{} do cartão: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(faturaService.pagar(id, ano, mes, pagamentoFaturaRequest));

    }

    @PutMapping("/{id}/banco")
    public ResponseEntity<CartaoResponse> ligarAoBanco(@PathVariable Long id, @Valid @RequestBody CartaoBancoRequest cartaoBancoRequest) {

        log.info("Ligando cartão {} ao banco", id);
        return ResponseEntity.status(HttpStatus.OK).body(bancoService.ligar(id, cartaoBancoRequest));

    }

    @PostMapping("/{id}/banco/sincronizar")
    public ResponseEntity<SincronizacaoBancoResponse> sincronizarComBanco(@PathVariable Long id) {

        log.info("Buscando no banco as transações do cartão: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(bancoService.sincronizar(id));

    }

    @GetMapping("/{id}/faturas/{ano}/{mes}/banco")
    public ResponseEntity<List<TransacaoBancoResponse>> faturaNoBanco(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes) {

        log.info("Retornando transações do banco na fatura {}/{} do cartão: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(bancoService.fatura(id, ano, mes));

    }

    @GetMapping("/{id}/faturas/{ano}/{mes}/conciliacao")
    public ResponseEntity<ConciliacaoResponse> conciliacao(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes) {

        log.info("Conciliando com o banco a fatura {}/{} do cartão: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.conciliacao(id, ano, mes));

    }

    @PostMapping("/{id}/faturas/{ano}/{mes}/conciliacao/confirmar")
    public ResponseEntity<ConciliacaoResponse> confirmarConciliacao(@PathVariable Long id, @PathVariable Integer ano, @PathVariable Integer mes,
                                                                    @Valid @RequestBody ConfirmarConciliacaoRequest confirmarConciliacaoRequest) {

        log.info("Confirmando conciliação da fatura {}/{} do cartão: {}", mes, ano, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.confirmar(id, ano, mes, confirmarConciliacaoRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/conciliar")
    public ResponseEntity<ConciliacaoResponse> conciliarTransacao(@PathVariable Long id, @PathVariable Long transacaoId,
                                                                  @Valid @RequestBody ConciliarRequest conciliarRequest) {

        log.info("Conciliando transação do banco {} do cartão: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.conciliar(id, transacaoId, conciliarRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/criar")
    public ResponseEntity<ConciliacaoResponse> criarDaTransacao(@PathVariable Long id, @PathVariable Long transacaoId,
                                                                @Valid @RequestBody CriarDoBancoRequest criarDoBancoRequest) {

        log.info("Criando lançamento a partir da transação do banco {} do cartão: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.criar(id, transacaoId, criarDoBancoRequest));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/ignorar")
    public ResponseEntity<ConciliacaoResponse> ignorarTransacao(@PathVariable Long id, @PathVariable Long transacaoId) {

        log.info("Ignorando transação do banco {} do cartão: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.ignorar(id, transacaoId));

    }

    @PostMapping("/{id}/banco/transacoes/{transacaoId}/desfazer")
    public ResponseEntity<ConciliacaoResponse> desfazerTransacao(@PathVariable Long id, @PathVariable Long transacaoId) {

        log.info("Desfazendo revisão da transação do banco {} do cartão: {}", transacaoId, id);
        return ResponseEntity.status(HttpStatus.OK).body(conciliacaoService.desfazer(id, transacaoId));

    }

}
