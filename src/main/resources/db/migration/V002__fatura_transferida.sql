-- Compra de cartão transferida à mão para uma fatura posterior à da data da compra.
-- Com a marca, editar o lançamento ou mudar o ciclo do cartão não a devolve sozinha.
alter table lancamentos add column fatura_transferida boolean not null default false;
