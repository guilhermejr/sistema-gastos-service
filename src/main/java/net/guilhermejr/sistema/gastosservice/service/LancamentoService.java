package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Recorrencia;
import net.guilhermejr.sistema.gastosservice.domain.enums.Escopo;
import net.guilhermejr.sistema.gastosservice.domain.enums.Repeticao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionNotFound;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class LancamentoService {

    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaService recorrenciaService;
    private final CategoriaService categoriaService;
    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final LancamentoMapper lancamentoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    public LancamentoResponse retornarUm(Long id) {

        return lancamentoMapper.mapObject(lancamentoDoUsuario(id));

    }

    /**
     * Inclui um lançamento avulso, uma série fixa (cria a primeira ocorrência; as demais
     * surgem quando os meses forem consultados) ou uma compra parcelada (cria todas as
     * parcelas). Devolve tudo o que foi criado.
     */
    @Transactional
    public List<LancamentoResponse> inserir(LancamentoRequest lancamentoRequest) {

        Lancamento base = montar(new Lancamento(), lancamentoRequest);
        base.setUsuario(usuario());

        Repeticao repeticao = Objects.requireNonNullElse(lancamentoRequest.getRepeticao(), Repeticao.UNICA);
        List<Lancamento> criados = switch (repeticao) {
            case UNICA -> List.of(lancamentoRepository.save(base));
            case FIXA -> {
                base.setRecorrencia(recorrenciaService.criar(base));
                yield List.of(lancamentoRepository.save(base));
            }
            case PARCELADA -> lancamentoRepository.saveAll(parcelar(base, lancamentoRequest.getParcelas()));
        };

        log.info("{} lançamento(s) incluído(s): {} {}", criados.size(), repeticao, base.getDescricao());
        return lancamentoMapper.mapList(criados);

    }

    /**
     * Altera um lançamento. Com escopo SEGUINTES:
     * <ul>
     *   <li>numa despesa/receita fixa, as próximas ocorrências pendentes e o molde da
     *   série passam a ser iguais a esta (valor, dia, categoria, conta...);</li>
     *   <li>numa compra parcelada, as próximas parcelas pendentes recebem descrição,
     *   categoria e conta/cartão — valor e data de cada parcela continuam os seus.</li>
     * </ul>
     */
    @Transactional
    public LancamentoResponse atualizar(Long id, LancamentoRequest lancamentoRequest, Escopo escopo) {

        Lancamento lancamento = lancamentoDoUsuario(id);
        exigeNaoPagoEmFatura(lancamento);
        LocalDate dataAnterior = lancamento.getData();

        montar(lancamento, lancamentoRequest);
        Lancamento lancamentoSave = lancamentoRepository.save(lancamento);

        if (escopo == Escopo.SEGUINTES && lancamento.getRecorrencia() != null) {
            propagarParaRecorrencia(lancamentoSave, dataAnterior);
        } else if (escopo == Escopo.SEGUINTES && lancamento.getParcelamento() != null) {
            propagarParaParcelas(lancamentoSave);
        }

        return lancamentoMapper.mapObject(lancamentoSave);

    }

    @Transactional
    public LancamentoResponse alterarRealizado(Long id, boolean realizado) {

        Lancamento lancamento = lancamentoDoUsuario(id);
        if (lancamento.getCartao() != null) {
            throw new ExceptionDefault("Lançamento de cartão é realizado pelo pagamento da fatura.");
        }
        lancamento.setRealizado(realizado);
        return lancamentoMapper.mapObject(lancamentoRepository.save(lancamento));

    }

    /**
     * Apaga um lançamento. Com escopo SEGUINTES, apaga também as próximas ocorrências
     * pendentes da série; numa despesa fixa, a série é encerrada e nada mais é gerado.
     */
    @Transactional
    public void apagar(Long id, Escopo escopo) {

        Lancamento lancamento = lancamentoDoUsuario(id);
        exigeNaoPagoEmFatura(lancamento);

        if (escopo == Escopo.SEGUINTES && lancamento.getRecorrencia() != null) {
            recorrenciaService.encerrar(lancamento.getRecorrencia(), lancamento.getData());
        } else if (escopo == Escopo.SEGUINTES && lancamento.getParcelamento() != null) {
            lancamentoRepository.deleteAll(lancamentoRepository
                    .findAllByParcelamentoAndParcelaGreaterThanEqualAndRealizadoFalse(lancamento.getParcelamento(), lancamento.getParcela()));
        }

        lancamentoRepository.findById(id).ifPresent(lancamentoRepository::delete);

    }

    /**
     * Devolve o lançamento somente se ele for do usuário autenticado. Lançamento de outro
     * usuário responde "não encontrado" em vez de 403 — não revela que o id existe.
     */
    public Lancamento lancamentoDoUsuario(Long id) {

        return lancamentoRepository.findByIdAndUsuario(id, usuario())
                .orElseThrow(() -> new ExceptionNotFound("Lançamento não encontrado: " + id));

    }

    /**
     * Preenche o lançamento a partir da requisição, validando dono, situação e tipo de
     * categoria, conta e cartão. Numa alteração, manter a mesma categoria, conta ou cartão
     * é permitido mesmo que tenham sido desativados depois; trocar por outro, não.
     */
    private Lancamento montar(Lancamento lancamento, LancamentoRequest lancamentoRequest) {

        if ((lancamentoRequest.getContaId() == null) == (lancamentoRequest.getCartaoId() == null)) {
            throw new ExceptionDefault("Informe a conta ou o cartão do lançamento — um dos dois.");
        }

        Categoria categoria = lancamento.getCategoria() != null
                && lancamento.getCategoria().getId().equals(lancamentoRequest.getCategoriaId())
                && lancamento.getCategoria().getTipo() == lancamentoRequest.getTipo()
                ? lancamento.getCategoria()
                : categoriaService.categoriaParaLancamento(lancamentoRequest.getCategoriaId(), lancamentoRequest.getTipo());

        lancamento.setTipo(lancamentoRequest.getTipo());
        lancamento.setDescricao(lancamentoRequest.getDescricao().trim());
        lancamento.setValor(converteStringUtil.toValorPositivo(lancamentoRequest.getValor()));
        lancamento.setData(converteStringUtil.toLocalDate(lancamentoRequest.getData()));
        lancamento.setCategoria(categoria);

        if (lancamentoRequest.getContaId() != null) {
            Conta conta = lancamento.getConta() != null && lancamento.getConta().getId().equals(lancamentoRequest.getContaId())
                    ? lancamento.getConta()
                    : contaService.contaAtivaDoUsuario(lancamentoRequest.getContaId());
            lancamento.setConta(conta);
            lancamento.setCartao(null);
            lancamento.setFatura(null);
            lancamento.setRealizado(Boolean.TRUE.equals(lancamentoRequest.getRealizado()));
        } else {
            Cartao cartao = lancamento.getCartao() != null && lancamento.getCartao().getId().equals(lancamentoRequest.getCartaoId())
                    ? lancamento.getCartao()
                    : cartaoService.cartaoAtivoDoUsuario(lancamentoRequest.getCartaoId());
            lancamento.setCartao(cartao);
            lancamento.setConta(null);
            lancamento.setFatura(cartaoService.faturaDaCompra(cartao, lancamento));
            lancamento.setRealizado(false);
        }

        return lancamento;

    }

    /**
     * Uma parcela por mês, no mesmo dia da compra. Só a primeira pode nascer realizada;
     * em cartão, cada parcela cai na fatura do seu mês.
     */
    private List<Lancamento> parcelar(Lancamento base, Integer quantidade) {

        if (quantidade == null || quantidade < 2) {
            throw new ExceptionDefault("Informe em quantas parcelas (2 ou mais).");
        }

        UUID parcelamento = UUID.randomUUID();
        List<BigDecimal> valores = CalendarioUtil.dividirEmParcelas(base.getValor(), quantidade);
        if (valores.get(quantidade - 1).signum() == 0) {
            throw new ExceptionDefault("O valor é pequeno demais para " + quantidade + " parcelas.");
        }
        int dia = base.getData().getDayOfMonth();
        List<Lancamento> parcelas = new ArrayList<>();

        for (int i = 0; i < quantidade; i++) {
            Lancamento parcela = i == 0 ? base : copiar(base);
            parcela.setValor(valores.get(i));
            parcela.setData(CalendarioUtil.mesesDepois(base.getData(), dia, i));
            if (parcela.getCartao() != null) {
                parcela.setFatura(cartaoService.faturaDaCompra(parcela.getCartao(), parcela));
            }
            if (i > 0) {
                parcela.setRealizado(false);
            }
            parcela.setParcela(i + 1);
            parcela.setTotalParcelas(quantidade);
            parcela.setParcelamento(parcelamento);
            parcelas.add(parcela);
        }

        return parcelas;

    }

    private void propagarParaRecorrencia(Lancamento lancamento, LocalDate dataAnterior) {

        Recorrencia recorrencia = lancamento.getRecorrencia();
        recorrenciaService.copiarParaMolde(lancamento, recorrencia);
        int dia = recorrencia.getDia();
        recorrencia.setGeradoAte(CalendarioUtil.diaNoMes(YearMonth.from(recorrencia.getGeradoAte()), dia));

        List<Lancamento> seguintes = lancamentoRepository
                .findAllByRecorrenciaAndDataGreaterThanEqualAndRealizadoFalse(recorrencia, dataAnterior)
                .stream().filter(l -> !l.getId().equals(lancamento.getId())).toList();

        for (Lancamento seguinte : seguintes) {
            seguinte.setTipo(lancamento.getTipo());
            seguinte.setDescricao(lancamento.getDescricao());
            seguinte.setValor(lancamento.getValor());
            seguinte.setCategoria(lancamento.getCategoria());
            seguinte.setData(CalendarioUtil.diaNoMes(YearMonth.from(seguinte.getData()), dia));
            moverPara(seguinte, lancamento);
        }
        lancamentoRepository.saveAll(seguintes);

    }

    private void propagarParaParcelas(Lancamento lancamento) {

        List<Lancamento> seguintes = lancamentoRepository
                .findAllByParcelamentoAndParcelaGreaterThanEqualAndRealizadoFalse(lancamento.getParcelamento(), lancamento.getParcela())
                .stream().filter(l -> !l.getId().equals(lancamento.getId())).toList();

        for (Lancamento seguinte : seguintes) {
            seguinte.setDescricao(lancamento.getDescricao());
            seguinte.setCategoria(lancamento.getCategoria());
            moverPara(seguinte, lancamento);
        }
        lancamentoRepository.saveAll(seguintes);

    }

    /** Leva o lançamento para a mesma conta ou cartão do modelo, recalculando a fatura. */
    private void moverPara(Lancamento lancamento, Lancamento modelo) {

        lancamento.setConta(modelo.getConta());
        lancamento.setCartao(modelo.getCartao());
        lancamento.setFatura(modelo.getCartao() == null ? null : cartaoService.faturaDaCompra(modelo.getCartao(), lancamento));

    }

    private Lancamento copiar(Lancamento base) {

        Lancamento copia = new Lancamento();
        copia.setTipo(base.getTipo());
        copia.setDescricao(base.getDescricao());
        copia.setCategoria(base.getCategoria());
        copia.setConta(base.getConta());
        copia.setCartao(base.getCartao());
        copia.setRealizado(base.getRealizado());
        copia.setUsuario(base.getUsuario());
        return copia;

    }

    /** O que já foi quitado numa fatura só muda depois de estornar o pagamento. */
    private void exigeNaoPagoEmFatura(Lancamento lancamento) {

        if (lancamento.getPagamento() != null) {
            throw new ExceptionDefault("Este lançamento já foi pago na fatura. Estorne o pagamento da fatura antes de mudá-lo.");
        }

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
