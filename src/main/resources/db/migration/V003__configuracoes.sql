-- Preferências de cada usuário, uma linha por usuário. Quem não tem linha usa os padrões.
-- dia_inicio_ciclo: dia em que começa o "mês" do dashboard e do relatório (1 = do dia 1 ao último dia).
create table configuracoes (
    id bigserial not null,
    dia_inicio_ciclo smallint not null default 1,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint uk_configuracoes_usuario unique (usuario),
    constraint ck_configuracoes_dia_inicio_ciclo check (dia_inicio_ciclo between 1 and 28)
);
