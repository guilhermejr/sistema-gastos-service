# gastos-service

Microsserviço de **controle financeiro** — categorias, contas, cartões de crédito, despesas, receitas, transferências e relatório mensal.

## Stack

| Item | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 |

| Porta | Context path | Perfil exigido |
|---|---|---|
| 9008 | `/gastos-service/` | `ROLE_GASTOS` |

## Endpoints

Valores monetários são **enviados** como texto no formato brasileiro (`"1.234,56"`) e **devolvidos** como número. Datas são enviadas como `dd/MM/yyyy` e devolvidas como `yyyy-MM-dd`.

### `/dashboard` e `/relatorios`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/dashboard` | receitas e despesas do mês, Saldo Geral, Saldo Total, faturas atuais, contas do saldo geral, próximos 5 a pagar e a receber |
| `GET` | `/relatorios/{ano}/{mes}` | lançamentos em conta do mês e, de cada cartão, só o total da fatura que vence no mês (`faturas`), com totais de entradas e saídas, realizados e pendentes |

### `/categorias`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/categorias?tipo=D\|R` | lista (o filtro é opcional) |
| `POST` | `/categorias` | cadastra |
| `GET` / `PUT` / `DELETE` | `/categorias/{id}` | busca, altera, apaga (só sem uso) |
| `PUT` | `/categorias/{id}/desativar` / `ativar` | tira ou devolve ao uso |

### `/contas`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/contas?ativas=true` | lista com o saldo calculado |
| `POST` | `/contas` | cadastra (`nome`, `saldoInicial`, `somaSaldoGeral`) |
| `GET` / `PUT` | `/contas/{id}` | busca, altera |
| `PUT` | `/contas/{id}/desativar` / `ativar` | desativar exige saldo zero e nenhum cartão ativo ou despesa fixa usando a conta |
| `GET` | `/contas/{id}/movimentacoes?ano=&mes=` | depósitos, saques, transferências e faturas pagas no mês |

### `/cartoes`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/cartoes?ativos=true` | lista |
| `POST` | `/cartoes` | cadastra (`nome`, `diaVencimento`, `diasFechamento`, `contaId`) |
| `GET` / `PUT` | `/cartoes/{id}` | busca, altera |
| `PUT` | `/cartoes/{id}/desativar` / `ativar` | tira ou devolve ao uso |
| `GET` | `/cartoes/{id}/faturas/atual` | fatura que recebe uma compra feita hoje |
| `GET` | `/cartoes/{id}/faturas/{ano}/{mes}` | fatura com vencimento no mês |
| `POST` | `/cartoes/{id}/faturas/{ano}/{mes}/pagamento` | paga o pendente (`contaId` e `data` opcionais) |

### `/lancamentos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/lancamentos` | despesa (`D`) ou receita (`R`) em conta **ou** cartão; `repeticao` `UNICA`, `FIXA` ou `PARCELADA` (+ `parcelas`) |
| `GET` | `/lancamentos/{id}` | busca |
| `PUT` | `/lancamentos/{id}?escopo=UNICO\|SEGUINTES` | altera só este ou também os próximos da série |
| `PUT` | `/lancamentos/{id}/realizado` | marca como pago/recebido (só em conta) |
| `PUT` | `/lancamentos/{id}/fatura` | transfere a compra de cartão para a fatura de outro mês (`{ "ano", "mes" }`), normalmente a próxima; não aceita mês anterior à fatura da compra nem compra já paga |
| `DELETE` | `/lancamentos/{id}?escopo=UNICO\|SEGUINTES` | apaga só este ou também os próximos (encerra a despesa fixa) |

### `/movimentacoes`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/movimentacoes` | `TRANSFERENCIA` (origem e destino), `DEPOSITO` (destino) ou `SAQUE` (origem) |
| `DELETE` | `/movimentacoes/{id}` | apaga; num pagamento de fatura, é o estorno |

## Regras

- **Saldo da conta** = saldo inicial + receitas realizadas − despesas realizadas + depósitos e transferências recebidas − saques, transferências enviadas e faturas pagas. Lançamento pendente não conta.
- **Saldo Geral** soma as contas ativas marcadas com `somaSaldoGeral`; **Saldo Total** soma todas as contas ativas.
- **Cartão**: a fatura fecha `diasFechamento` dias antes do vencimento. A compra precisa ser **anterior** à data de fechamento para entrar na fatura; no próprio dia já vai para a seguinte. Compra no cartão não mexe em conta: quem debita a conta é o pagamento da fatura, que também marca as compras como realizadas. Uma compra ainda não paga pode ser transferida para uma fatura seguinte; a escolha é mantida mesmo se o lançamento for editado ou o vencimento do cartão mudar.
- **Relatório mensal**: lançamentos em conta pela data; cartão de crédito só pelo total de cada fatura, na data de vencimento. O que já foi pago da fatura conta como realizado.
- **Despesa fixa** repete todo mês no mesmo dia (dia 31 vira o último dia em meses curtos). As ocorrências são criadas sob demanda, quando o mês é consultado.
- **Parcelado**: o valor informado é o total; a divisão é em centavos e a sobra vai para a primeira parcela.

## Banco de dados

PostgreSQL, com schema versionado por **Flyway** (migrations em `src/main/resources/db/migration`):

- `V001__Inicial.sql`
- `V002__fatura_transferida.sql`

> No Spring Boot 4 a autoconfiguração do Flyway passou a viver no módulo `spring-boot-flyway`. Sem essa dependência o Flyway é ignorado **em silêncio**. Ela está declarada no `pom.xml`; não remova.

**Entidades:** `Categoria`, `Conta`, `Cartao`, `Lancamento`, `Recorrencia`, `Movimentacao`.

## Configuração

| Origem | O que vem de lá |
|---|---|
| **Vault** (`secret/application`) | segredos compartilhados: `JWTSecret`, credenciais de e-mail, AWS, Eureka |
| **Vault** (`secret/gastos-service`) | `eurekaHostname` e as credenciais do banco (`gastosDBHost`, `gastosDBBase`, `gastosDBUser`, `gastosDBPass`) |
| **Config Server** | `server.port`, `context-path`, datasource |

`VAULT_TOKEN` é obrigatória e não tem valor padrão.

## Como executar

```bash
./mvnw clean package -DskipTests
VAULT_TOKEN=<seu-token> java -jar target/gastos-service-*.jar --spring.profiles.active=dev
```

Testes de unidade (não precisam de Vault nem banco): `./mvnw test`.

> **Dependências no ar:** o serviço só sobe com o **Vault**, o **Config Server** e o **Eureka** disponíveis, além do banco PostgreSQL.

### Docker

O `Dockerfile` espera o jar na raiz do projeto com o nome `sistema-gastos-service.jar`:

```bash
./mvnw clean package -DskipTests
cp target/gastos-service-*.jar sistema-gastos-service.jar
docker build --build-arg VAULT_HOST=<host> --build-arg VAULT_TOKEN=<token> \
  --build-arg CONFIG_SERVER_USER=<usuario> --build-arg CONFIG_SERVER_PASS=<senha> \
  -t gastos-service .
```
