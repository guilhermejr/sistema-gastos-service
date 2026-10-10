-- Lançamento a que a transação do banco foi ligada (conciliada ou importada). Um
-- lançamento casa com uma transação só. Apagar o lançamento solta a transação, que
-- volta a ser tratada como a revisar.
alter table transacoes_banco add column lancamento_id bigint;
alter table transacoes_banco add constraint fk_transacoes_banco_lancamento
    foreign key (lancamento_id) references lancamentos (id) on delete set null;

create unique index uk_transacoes_banco_lancamento on transacoes_banco (lancamento_id);
