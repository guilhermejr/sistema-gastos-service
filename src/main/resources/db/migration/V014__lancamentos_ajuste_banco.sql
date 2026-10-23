-- Receita lançada pela conciliação da conta para fechar a diferença com o saldo do banco
-- (rendimento da aplicação automática). Não tem transação no extrato: fica fora da conciliação.
alter table lancamentos add column ajuste_banco boolean not null default false;
