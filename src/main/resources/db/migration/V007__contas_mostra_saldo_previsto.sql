-- Se o dashboard mostra o saldo da conta no fim do ciclo. As existentes continuam mostrando.
alter table contas add column mostra_saldo_previsto boolean not null default true;
