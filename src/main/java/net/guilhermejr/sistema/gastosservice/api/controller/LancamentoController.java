package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.FaturaDestinoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.RealizadoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.domain.enums.Escopo;
import net.guilhermejr.sistema.gastosservice.service.LancamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/lancamentos")
public class LancamentoController {

    private final LancamentoService lancamentoService;

    @GetMapping("/{id}")
    public ResponseEntity<LancamentoResponse> retornarUm(@PathVariable Long id) {

        log.info("Recuperando um lançamento: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(lancamentoService.retornarUm(id));

    }

    /** Devolve todos os lançamentos criados — mais de um quando parcelado. */
    @PostMapping
    public ResponseEntity<List<LancamentoResponse>> inserir(@Valid @RequestBody LancamentoRequest lancamentoRequest) {

        log.info("Inserindo lançamento");
        return ResponseEntity.status(HttpStatus.CREATED).body(lancamentoService.inserir(lancamentoRequest));

    }

    @PutMapping("/{id}")
    public ResponseEntity<LancamentoResponse> atualizar(@PathVariable Long id, @RequestParam(defaultValue = "UNICO") Escopo escopo,
                                                        @Valid @RequestBody LancamentoRequest lancamentoRequest) {

        log.info("Atualizando lançamento {} ({})", id, escopo);
        return ResponseEntity.status(HttpStatus.OK).body(lancamentoService.atualizar(id, lancamentoRequest, escopo));

    }

    @PutMapping("/{id}/realizado")
    public ResponseEntity<LancamentoResponse> alterarRealizado(@PathVariable Long id, @Valid @RequestBody RealizadoRequest realizadoRequest) {

        log.info("Lançamento {} realizado: {}", id, realizadoRequest.getRealizado());
        return ResponseEntity.status(HttpStatus.OK).body(lancamentoService.alterarRealizado(id, realizadoRequest.getRealizado()));

    }

    /** Muda a compra de cartão de fatura (normalmente para a próxima). */
    @PutMapping("/{id}/fatura")
    public ResponseEntity<LancamentoResponse> transferirFatura(@PathVariable Long id, @Valid @RequestBody FaturaDestinoRequest faturaDestinoRequest) {

        log.info("Transferindo lançamento {} para a fatura {}/{}", id, faturaDestinoRequest.getMes(), faturaDestinoRequest.getAno());
        return ResponseEntity.status(HttpStatus.OK).body(lancamentoService.transferirFatura(id,
                faturaDestinoRequest.getAno(), faturaDestinoRequest.getMes()));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id, @RequestParam(defaultValue = "UNICO") Escopo escopo) {

        log.info("Apagando lançamento {} ({})", id, escopo);
        lancamentoService.apagar(id, escopo);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }

}
