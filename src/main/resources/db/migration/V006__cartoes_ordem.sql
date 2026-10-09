-- Ordem em que os cartões aparecem, escolhida pelo usuário. Os existentes começam na
-- ordem por nome, a mesma em que eram listados até aqui.
alter table cartoes add column ordem integer;
update cartoes c set ordem = o.posicao
    from (select id, row_number() over (partition by usuario order by nome, id) as posicao from cartoes) o
    where c.id = o.id;
alter table cartoes alter column ordem set not null;
