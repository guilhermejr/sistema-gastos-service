-- Conta ou cartão que já vem escolhido ao incluir uma despesa. No máximo um dos dois;
-- os dois vazios = a escolha automática do frontend (primeiro cartão ativo, senão a primeira conta).
alter table configuracoes add column despesa_conta_id bigint;
alter table configuracoes add column despesa_cartao_id bigint;
alter table configuracoes add constraint fk_configuracoes_despesa_conta foreign key (despesa_conta_id) references contas (id);
alter table configuracoes add constraint fk_configuracoes_despesa_cartao foreign key (despesa_cartao_id) references cartoes (id);
alter table configuracoes add constraint ck_configuracoes_despesa_destino
    check (despesa_conta_id is null or despesa_cartao_id is null);
