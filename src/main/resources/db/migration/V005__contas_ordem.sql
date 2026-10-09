-- Ordem em que as contas aparecem, escolhida pelo usuário. As existentes começam na
-- ordem por nome, a mesma em que eram listadas até aqui.
alter table contas add column ordem integer;
update contas c set ordem = o.posicao
    from (select id, row_number() over (partition by usuario order by nome, id) as posicao from contas) o
    where c.id = o.id;
alter table contas alter column ordem set not null;
