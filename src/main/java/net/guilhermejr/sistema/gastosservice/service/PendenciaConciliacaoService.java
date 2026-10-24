package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.sistema.gastosservice.api.response.PendenciaConciliacaoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Quanto falta conciliar em cada cartão e conta ligados ao banco, para o dashboard
 * sinalizar e a sincronização automática avisar — sem abrir a conciliação.
 */
@RequiredArgsConstructor
@Service
public class PendenciaConciliacaoService {

    private final TransacaoBancoRepository transacaoBancoRepository;
    private final FaturaService faturaService;
    private final Clock clock;

    /** Até a fatura atual; nulo se o cartão não está ligado ou não há nada a conciliar. */
    public PendenciaConciliacaoResponse doCartao(Cartao cartao) {

        if (cartao.getBancoContaId() == null) {
            return null;
        }
        return pendencia(transacaoBancoRepository.faturasAConciliar(cartao, faturaService.mesDaFaturaAtual(cartao).atEndOfMonth()));

    }

    /**
     * Até o fim do mês atual, o mesmo que a conciliação da conta abre: inclui o que o banco
     * já agendou (salário, débitos), como o cartão inclui a fatura atual. Nulo se a conta não
     * está ligada ou não há nada a conciliar.
     */
    public PendenciaConciliacaoResponse daConta(Conta conta) {

        if (conta.getBancoContaId() == null) {
            return null;
        }
        return pendencia(transacaoBancoRepository.datasAConciliar(conta, YearMonth.now(clock).atEndOfMonth()));

    }

    public static int quantidade(PendenciaConciliacaoResponse pendencia) {
        return pendencia == null ? 0 : pendencia.getQuantidade();
    }

    static PendenciaConciliacaoResponse pendencia(List<LocalDate> datas) {

        return datas.stream().min(Comparator.naturalOrder()).map(YearMonth::from)
                .map(mes -> new PendenciaConciliacaoResponse(datas.size(), mes.getYear(), mes.getMonthValue()))
                .orElse(null);

    }

}
