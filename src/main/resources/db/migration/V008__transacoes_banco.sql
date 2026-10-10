-- Cartão ligado a um cartão do banco (Open Finance, via Pluggy). banco_conta_id é o id
-- da conta de crédito na Pluggy; banco_inicio_fatura, a primeira fatura a buscar.
alter table cartoes add column banco_conta_id varchar(64);
alter table cartoes add column banco_inicio_fatura date;

create unique index uk_cartoes_banco_conta on cartoes (banco_conta_id);

-- Transações do cartão como o banco as informa, para conciliar com os lançamentos.
-- banco_id é o id da transação na Pluggy: buscar de novo atualiza, não duplica.
create table transacoes_banco (
    id bigserial not null,
    cartao_id bigint not null,
    banco_id varchar(64) not null,
    tipo varchar(1) not null,
    descricao varchar(255) not null,
    valor numeric(19,2) not null,
    data date not null,
    data_compra date,
    fatura date not null,
    parcela integer,
    total_parcelas integer,
    pendente boolean not null,
    final_cartao varchar(4),
    categoria_banco varchar(255),
    situacao varchar(20) not null,
    criado timestamp without time zone,
    atualizado timestamp without time zone,
    usuario uuid not null,
    primary key (id),
    constraint fk_transacoes_banco_cartao foreign key (cartao_id) references cartoes (id),
    constraint ck_transacoes_banco_tipo check (tipo in ('D', 'R')),
    constraint ck_transacoes_banco_situacao check (situacao in ('A_REVISAR', 'CONCILIADA', 'IMPORTADA', 'IGNORADA'))
);

create unique index uk_transacoes_banco_banco_id on transacoes_banco (banco_id);
create index ix_transacoes_banco_cartao_fatura on transacoes_banco (cartao_id, fatura);
