package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.request.ConfiguracaoRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConfiguracaoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Configuracao;
import net.guilhermejr.sistema.gastosservice.domain.repository.ConfiguracaoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.Ciclo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository configuracaoRepository;
    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    public ConfiguracaoResponse retornar() {

        return responder(configuracao(usuario()));

    }

    @Transactional
    public ConfiguracaoResponse atualizar(ConfiguracaoRequest configuracaoRequest) {

        UUID usuario = usuario();
        Configuracao configuracao = configuracaoRepository.findByUsuario(usuario).orElseGet(() -> {
            Configuracao nova = Configuracao.padrao();
            nova.setUsuario(usuario);
            return nova;
        });
        configuracao.setDiaInicioCiclo(configuracaoRequest.getDiaInicioCiclo());

        Long contaId = configuracaoRequest.getDespesaContaId();
        Long cartaoId = configuracaoRequest.getDespesaCartaoId();
        if (contaId != null && cartaoId != null) {
            throw new ExceptionDefault("Escolha uma conta ou um cartão para a despesa, não os dois.");
        }
        // Só se escolhe o que está ativo. Se depois for desativado, a escolha fica guardada
        // e o frontend volta para a automática enquanto ele estiver desativado.
        configuracao.setDespesaConta(contaId == null ? null : contaService.contaAtivaDoUsuario(contaId));
        configuracao.setDespesaCartao(cartaoId == null ? null : cartaoService.cartaoAtivoDoUsuario(cartaoId));

        return responder(configuracaoRepository.save(configuracao));

    }

    /** Dia de início do ciclo mensal do usuário (1 se ele nunca configurou). */
    public int diaInicioCiclo(UUID usuario) {

        return configuracao(usuario).getDiaInicioCiclo();

    }

    /** O ciclo chamado pelo mês {@code ano}/{@code mes}, pelo dia configurado do usuário. */
    public Ciclo ciclo(UUID usuario, YearMonth nome) {

        return Ciclo.chamado(nome, diaInicioCiclo(usuario));

    }

    private Configuracao configuracao(UUID usuario) {

        return configuracaoRepository.findByUsuario(usuario).orElseGet(Configuracao::padrao);

    }

    private ConfiguracaoResponse responder(Configuracao configuracao) {

        return ConfiguracaoResponse.builder()
                .diaInicioCiclo(configuracao.getDiaInicioCiclo())
                .despesaContaId(configuracao.getDespesaConta() == null ? null : configuracao.getDespesaConta().getId())
                .despesaCartaoId(configuracao.getDespesaCartao() == null ? null : configuracao.getDespesaCartao().getId())
                .build();

    }

    private UUID usuario() {
        return authenticationCurrentUserService.getCurrentUser().getId();
    }

}
