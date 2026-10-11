package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** Como está a conexão da Pluggy com o banco. Horários em UTC. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConexaoBancoResponse {

    /** Falso para quem não tem a integração: o resto vem vazio. */
    private boolean disponivel;
    private String status;
    /** Última vez que a Pluggy buscou os dados no banco. */
    private LocalDateTime atualizado;
    /** Próxima busca automática da Pluggy. */
    private LocalDateTime proximaAtualizacao;
    private LocalDateTime consentimentoExpira;
    /** O que precisa de atenção; vazio quando está tudo certo. */
    private List<String> alertas;

}
