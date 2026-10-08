package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.ConfiguracaoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConfiguracaoResponse;
import net.guilhermejr.sistema.gastosservice.service.ConfiguracaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/configuracoes")
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    @GetMapping
    public ResponseEntity<ConfiguracaoResponse> retornar() {

        log.info("Retornando configurações");
        return ResponseEntity.status(HttpStatus.OK).body(configuracaoService.retornar());

    }

    @PutMapping
    public ResponseEntity<ConfiguracaoResponse> atualizar(@Valid @RequestBody ConfiguracaoRequest configuracaoRequest) {

        log.info("Atualizando configurações: {}", configuracaoRequest);
        return ResponseEntity.status(HttpStatus.OK).body(configuracaoService.atualizar(configuracaoRequest));

    }

}
