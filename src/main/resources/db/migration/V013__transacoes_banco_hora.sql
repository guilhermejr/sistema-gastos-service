-- Hora da transação do cartão no fuso de São Paulo, quando o banco a informa. Vazia nas
-- parcelas (a data é a da parcela, à meia-noite) e nas transações da conta.
alter table transacoes_banco add column hora time;
