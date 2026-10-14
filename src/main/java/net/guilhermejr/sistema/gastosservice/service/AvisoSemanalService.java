package net.guilhermejr.sistema.gastosservice.service;

import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.api.response.ItemAgendaResponse;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aviso de segunda-feira com o que vence na semana — de hoje até domingo — e o que já
 * está atrasado, a pagar e a receber. Vai por push, que chega a uma pessoa só: o usuário
 * avisado é {@code avisoUsuario} ou, sem ele, o mesmo {@code pluggyUsuario} da integração
 * com o banco. Sem nenhum dos dois, o aviso fica desligado.
 */
@Log4j2
@Service
public class AvisoSemanalService {

    public static final String TITULO = "Vencimentos da semana";

    /** O Pushover recusa mensagens com mais de 1024 caracteres. */
    static final int LIMITE_MENSAGEM = 1024;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final ConverteStringUtil CONVERTE = new ConverteStringUtil();

    private final DashboardService dashboardService;
    private final Clock clock;
    private final String usuario;

    public AvisoSemanalService(DashboardService dashboardService, Clock clock,
                               @Value("${avisoUsuario:${pluggyUsuario:}}") String usuario) {
        this.dashboardService = dashboardService;
        this.clock = clock;
        this.usuario = usuario;
    }

    /** A mensagem da semana, ou vazio quando não há nada a pagar nem a receber até domingo. */
    @Transactional
    public Optional<String> montar() {

        if (usuario.isBlank()) {
            log.info("Aviso semanal desligado: sem avisoUsuario nem pluggyUsuario no Vault");
            return Optional.empty();
        }

        UUID id = UUID.fromString(usuario);
        LocalDate domingo = LocalDate.now(clock).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return mensagem(dashboardService.agendaAte(id, TipoLancamento.D, domingo),
                dashboardService.agendaAte(id, TipoLancamento.R, domingo));

    }

    /**
     * Uma seção por lista não vazia, com o total e um item por linha. Se passar do limite
     * do Pushover, mostra menos itens por seção e diz quantos ficaram de fora.
     */
    static Optional<String> mensagem(List<ItemAgendaResponse> pagar, List<ItemAgendaResponse> receber) {

        if (pagar.isEmpty() && receber.isEmpty()) {
            return Optional.empty();
        }

        int maximo = Math.max(pagar.size(), receber.size());
        String mensagem;
        do {
            List<String> secoes = new ArrayList<>();
            if (!pagar.isEmpty()) {
                secoes.add(secao("A pagar", pagar, maximo));
            }
            if (!receber.isEmpty()) {
                secoes.add(secao("A receber", receber, maximo));
            }
            mensagem = String.join("\n\n", secoes);
            maximo--;
        } while (mensagem.length() > LIMITE_MENSAGEM && maximo > 0);

        return Optional.of(mensagem);

    }

    private static String secao(String titulo, List<ItemAgendaResponse> itens, int maximo) {

        BigDecimal total = itens.stream().map(ItemAgendaResponse::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder secao = new StringBuilder(titulo + " (" + CONVERTE.formatarMoeda(total) + "):");

        itens.stream().limit(maximo).forEach(item -> {
            secao.append("\n• ").append(item.getData().format(DIA)).append(' ').append(item.getDescricao())
                    .append(": ").append(CONVERTE.formatarMoeda(item.getValor()));
            if (Boolean.TRUE.equals(item.getAtrasado())) {
                secao.append(" (atrasado)");
            }
        });
        if (itens.size() > maximo) {
            secao.append("\n… e mais ").append(itens.size() - maximo);
        }
        return secao.toString();

    }

}
