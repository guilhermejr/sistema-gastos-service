-- Conta ligada à conta corrente no banco (Open Finance, via Pluggy). banco_inicio é o
-- primeiro dia buscado; banco_sincronizado (UTC) e banco_saldo, a última busca.
alter table contas add column banco_conta_id varchar(64);
alter table contas add column banco_inicio date;
alter table contas add column banco_sincronizado timestamp;
alter table contas add column banco_saldo numeric(19,2);

create unique index uk_contas_banco_conta on contas (banco_conta_id);

-- transacoes_banco passa a guardar também as da conta corrente: cada uma é de um cartão
-- (com fatura) ou de uma conta (sem fatura).
alter table transacoes_banco alter column cartao_id drop not null;
alter table transacoes_banco alter column fatura drop not null;
alter table transacoes_banco add column conta_id bigint;
alter table transacoes_banco add constraint fk_transacoes_banco_conta foreign key (conta_id) references contas (id);
alter table transacoes_banco add constraint ck_transacoes_banco_cartao_ou_conta check ((cartao_id is null) <> (conta_id is null));
alter table transacoes_banco add constraint ck_transacoes_banco_fatura check ((cartao_id is null) = (fatura is null));

create index ix_transacoes_banco_conta_data on transacoes_banco (conta_id, data);

-- Na conta, a transação pode casar com uma movimentação (transferência, pagamento de
-- fatura) em vez de um lançamento. Apagar a movimentação solta a transação.
alter table transacoes_banco add column movimentacao_id bigint;
alter table transacoes_banco add constraint fk_transacoes_banco_movimentacao
    foreign key (movimentacao_id) references movimentacoes (id) on delete set null;

create unique index uk_transacoes_banco_movimentacao on transacoes_banco (movimentacao_id);
