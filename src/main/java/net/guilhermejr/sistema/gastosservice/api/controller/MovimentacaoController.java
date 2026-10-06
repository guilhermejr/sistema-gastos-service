package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.MovimentacaoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.MovimentacaoResponse;
import net.guilhermejr.sistema.gastosservice.service.MovimentacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;

    @PostMapping
    public ResponseEntity<MovimentacaoResponse> inserir(@Valid @RequestBody MovimentacaoRequest movimentacaoRequest) {

        log.info("Inserindo movimentação: {}", movimentacaoRequest.getTipo());
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoService.inserir(movimentacaoRequest));

    }

    /** Apagar um pagamento de fatura é estorná-lo: os lançamentos voltam a pendentes. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {

        log.info("Apagando movimentação: {}", id);
        movimentacaoService.apagar(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }

}
