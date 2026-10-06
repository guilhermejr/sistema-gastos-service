package net.guilhermejr.sistema.gastosservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.request.CategoriaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.CategoriaResponse;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.service.CategoriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> retornar(@RequestParam(required = false) TipoLancamento tipo) {

        log.info("Retornando categorias: {}", tipo);
        return ResponseEntity.status(HttpStatus.OK).body(categoriaService.retornar(tipo));

    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponse> retornarUm(@PathVariable Long id) {

        log.info("Recuperando uma categoria: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(categoriaService.retornarUm(id));

    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> inserir(@Valid @RequestBody CategoriaRequest categoriaRequest) {

        log.info("Inserindo categoria");
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.inserir(categoriaRequest));

    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest categoriaRequest) {

        log.info("Atualizando categoria: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(categoriaService.atualizar(id, categoriaRequest));

    }

    @PutMapping("/{id}/desativar")
    public ResponseEntity<CategoriaResponse> desativar(@PathVariable Long id) {

        log.info("Desativando categoria: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(categoriaService.alterarAtivo(id, false));

    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<CategoriaResponse> ativar(@PathVariable Long id) {

        log.info("Ativando categoria: {}", id);
        return ResponseEntity.status(HttpStatus.OK).body(categoriaService.alterarAtivo(id, true));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {

        log.info("Apagando categoria: {}", id);
        categoriaService.apagar(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }

}
