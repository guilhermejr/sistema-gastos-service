package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.ContaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.MovimentacaoResponse;
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

    @PutMapping("/{id}/ativar")
    public ResponseEntity<ContaResponse> ativar(@PathVariable Long id) {

        log.info("Ativando conta: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(contaService.ativar(id));

    }

}
