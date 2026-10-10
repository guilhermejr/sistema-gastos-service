-- Limite do cartão como o banco informou na última busca. O utilizado é a diferença.
alter table cartoes add column banco_limite numeric(19,2);
alter table cartoes add column banco_limite_disponivel numeric(19,2);
