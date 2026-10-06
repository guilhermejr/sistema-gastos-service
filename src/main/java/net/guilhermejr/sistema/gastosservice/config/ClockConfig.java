package net.guilhermejr.sistema.gastosservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /**
     * "Hoje" decide fatura atual, mês do dashboard e itens atrasados. O fuso é fixo
     * para não depender do TZ da máquina: localmente e no container dá o mesmo dia.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bahia"));
    }

}
