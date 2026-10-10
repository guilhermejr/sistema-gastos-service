package net.guilhermejr.sistema.gastosservice.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.service.BancoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/banco")
public class BancoController {

    private final BancoService bancoService;

    @GetMapping("/cartoes")
    public ResponseEntity<List<CartaoBancoResponse>> cartoes() {

        log.info("Retornando cartões do banco");
        return ResponseEntity.status(HttpStatus.OK).body(bancoService.cartoesDoBanco());

    }

}
