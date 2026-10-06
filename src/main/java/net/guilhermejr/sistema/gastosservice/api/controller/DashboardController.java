package net.guilhermejr.sistema.gastosservice.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.response.DashboardResponse;
import net.guilhermejr.sistema.gastosservice.api.response.RelatorioMensalResponse;
import net.guilhermejr.sistema.gastosservice.service.DashboardService;
import net.guilhermejr.sistema.gastosservice.service.RelatorioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAnyRole('GASTOS')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RelatorioService relatorioService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {

        log.info("Carregando dashboard");
        return ResponseEntity.status(HttpStatus.OK).body(dashboardService.carregar());

    }

    @GetMapping("/relatorios/{ano}/{mes}")
    public ResponseEntity<RelatorioMensalResponse> relatorioMensal(@PathVariable Integer ano, @PathVariable Integer mes) {

        log.info("Relatório de {}/{}", mes, ano);
        return ResponseEntity.status(HttpStatus.OK).body(relatorioService.mensal(ano, mes));

    }

}
