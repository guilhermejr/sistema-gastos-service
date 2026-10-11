package net.guilhermejr.sistema.gastosservice.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.response.CartaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ConexaoBancoResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ContaBancoResponse;
import net.guilhermejr.sistema.gastosservice.service.BancoService;
import net.guilhermejr.sistema.gastosservice.service.ConexaoBancoService;
import net.guilhermejr.sistema.gastosservice.service.ContaBancoService;
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
    private final ContaBancoService contaBancoService;
    private final ConexaoBancoService conexaoBancoService;

    @GetMapping("/cartoes")
    public ResponseEntity<List<CartaoBancoResponse>> cartoes() {

        log.info("Retornando cartões do banco");
        return ResponseEntity.status(HttpStatus.OK).body(bancoService.cartoesDoBanco());

    }

    @GetMapping("/contas")
    public ResponseEntity<List<ContaBancoResponse>> contas() {

        log.info("Retornando contas correntes do banco");
        return ResponseEntity.status(HttpStatus.OK).body(contaBancoService.contasDoBanco());

    }

    @GetMapping("/conexao")
    public ResponseEntity<ConexaoBancoResponse> conexao() {

        log.info("Retornando a situação da conexão com o banco");
        return ResponseEntity.status(HttpStatus.OK).body(conexaoBancoService.conexao());

    }

}
