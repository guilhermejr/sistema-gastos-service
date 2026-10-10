-- Quando as transações do cartão foram buscadas no banco pela última vez (UTC).
alter table cartoes add column banco_sincronizado timestamp;
