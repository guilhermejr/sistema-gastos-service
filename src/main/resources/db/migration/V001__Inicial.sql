create table categorias (
    id bigserial not null,
    descricao varchar(255) not null,
    tipo varchar(1) not null,
    ativo boolean not null default true,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint ck_categorias_tipo check (tipo in ('D', 'R'))
);

create unique index uk_categorias_usuario_tipo_descricao on categorias (usuario, tipo, lower(descricao));

create table contas (
    id bigserial not null,
    nome varchar(255) not null,
    saldo_inicial numeric(19,2) not null,
    soma_saldo_geral boolean not null,
    ativo boolean not null default true,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id)
);

create unique index uk_contas_usuario_nome on contas (usuario, lower(nome));

create table cartoes (
    id bigserial not null,
    nome varchar(255) not null,
    dia_vencimento integer not null,
    dias_fechamento integer not null,
    conta_id bigint not null,
    ativo boolean not null default true,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint fk_cartoes_conta foreign key (conta_id) references contas (id),
    constraint ck_cartoes_dia_vencimento check (dia_vencimento between 1 and 31),
    constraint ck_cartoes_dias_fechamento check (dias_fechamento between 1 and 28)
);

create unique index uk_cartoes_usuario_nome on cartoes (usuario, lower(nome));

-- Molde de uma despesa/receita fixa. As ocorrências viram linhas de lancamentos
-- sob demanda, até a data que alguém consultar; gerado_ate marca a última criada.
create table recorrencias (
    id bigserial not null,
    tipo varchar(1) not null,
    descricao varchar(255) not null,
    valor numeric(19,2) not null,
    dia integer not null,
    categoria_id bigint not null,
    conta_id bigint,
    cartao_id bigint,
    gerado_ate date not null,
    ativo boolean not null default true,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint fk_recorrencias_categoria foreign key (categoria_id) references categorias (id),
    constraint fk_recorrencias_conta foreign key (conta_id) references contas (id),
    constraint fk_recorrencias_cartao foreign key (cartao_id) references cartoes (id),
    constraint ck_recorrencias_conta_ou_cartao check ((conta_id is null) <> (cartao_id is null))
);

-- Depósitos, saques, transferências e pagamentos de fatura: tudo o que mexe no
-- saldo de uma conta sem ser receita ou despesa.
create table movimentacoes (
    id bigserial not null,
    tipo varchar(20) not null,
    descricao varchar(255),
    valor numeric(19,2) not null,
    data date not null,
    conta_origem_id bigint,
    conta_destino_id bigint,
    cartao_id bigint,
    fatura date,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint fk_movimentacoes_conta_origem foreign key (conta_origem_id) references contas (id),
    constraint fk_movimentacoes_conta_destino foreign key (conta_destino_id) references contas (id),
    constraint fk_movimentacoes_cartao foreign key (cartao_id) references cartoes (id),
    constraint ck_movimentacoes_tipo check (tipo in ('DEPOSITO', 'SAQUE', 'TRANSFERENCIA', 'PAGAMENTO_FATURA'))
);

create index ix_movimentacoes_usuario_data on movimentacoes (usuario, data);
create index ix_movimentacoes_cartao_fatura on movimentacoes (cartao_id, fatura);

create table lancamentos (
    id bigserial not null,
    tipo varchar(1) not null,
    descricao varchar(255) not null,
    valor numeric(19,2) not null,
    data date not null,
    categoria_id bigint not null,
    conta_id bigint,
    cartao_id bigint,
    fatura date,
    realizado boolean not null,
    parcela integer,
    total_parcelas integer,
    parcelamento uuid,
    recorrencia_id bigint,
    pagamento_id bigint,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint fk_lancamentos_categoria foreign key (categoria_id) references categorias (id),
    constraint fk_lancamentos_conta foreign key (conta_id) references contas (id),
    constraint fk_lancamentos_cartao foreign key (cartao_id) references cartoes (id),
    constraint fk_lancamentos_recorrencia foreign key (recorrencia_id) references recorrencias (id),
    constraint fk_lancamentos_pagamento foreign key (pagamento_id) references movimentacoes (id),
    constraint ck_lancamentos_tipo check (tipo in ('D', 'R')),
    constraint ck_lancamentos_conta_ou_cartao check ((conta_id is null) <> (cartao_id is null)),
    constraint ck_lancamentos_fatura check ((cartao_id is null) = (fatura is null))
);

create index ix_lancamentos_usuario_data on lancamentos (usuario, data);
create index ix_lancamentos_cartao_fatura on lancamentos (cartao_id, fatura);
create index ix_lancamentos_parcelamento on lancamentos (parcelamento);
create index ix_lancamentos_recorrencia on lancamentos (recorrencia_id);
