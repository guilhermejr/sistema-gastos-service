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
| `GET` | `/dashboard` | receitas e despesas do ciclo mensal em que hoje cai (`ano`/`mes` é o nome do ciclo, `inicio`/`fim` as datas; mesma regra do relatório: cartão pelo total da fatura que vence no ciclo), Saldo Geral, Saldo Total, faturas atuais, contas do saldo geral, próximos 5 a pagar e a receber (`{ itens, temMais }`) |
| `GET` | `/agenda/pagar?quantidade=N` | os N primeiros a pagar (despesas em conta e faturas), 1 a 100, com `temMais` |
| `GET` | `/agenda/receber?quantidade=N` | os N primeiros a receber, 1 a 100, com `temMais` |
| `GET` | `/relatorios/{ano}/{mes}` | lançamentos em conta do ciclo chamado por `ano`/`mes` (`inicio`/`fim`) e, de cada cartão, só o total da fatura que vence no ciclo (`faturas`), com totais de entradas e saídas, realizados e pendentes; `transferencias` traz as transferências entre contas do ciclo, só para consulta (fora dos totais) |

### `/configuracoes`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/configuracoes` | preferências do usuário (`diaInicioCiclo`; 1 se nunca configurou) |
| `PUT` | `/configuracoes` | grava `{ "diaInicioCiclo": 1..28 }` |

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
| `GET` | `/contas/{id}/movimentacoes?ano=&mes=` | depósitos, saques, transferências e faturas pagas no ciclo mensal |

### `/cartoes`

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/cartoes?ativos=true` | lista |
| `POST` | `/cartoes` | cadastra (`nome`, `diaVencimento`, `diasFechamento`, `contaId`) |
| `GET` / `PUT` | `/cartoes/{id}` | busca, altera |
| `PUT` | `/cartoes/{id}/desativar` / `ativar` | tira ou devolve ao uso |
| `GET` | `/cartoes/{id}/faturas/atual` | fatura que recebe uma compra feita hoje |
| `GET` | `/cartoes/{id}/faturas/{ano}/{mes}` | fatura com vencimento no mês (`anteriorPaga` diz se a do mês anterior já recebeu pagamento) |
| `POST` | `/cartoes/{id}/faturas/{ano}/{mes}/pagamento` | paga o pendente (`contaId` e `data` opcionais) |

### `/lancamentos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/lancamentos` | despesa (`D`) ou receita (`R`) em conta **ou** cartão; `repeticao` `UNICA`, `FIXA` ou `PARCELADA` (+ `parcelas`); em cartão, `fatura` (`{ "ano", "mes" }`) opcional escolhe a fatura quando não é a da data |
| `GET` | `/lancamentos/{id}` | busca |
| `PUT` | `/lancamentos/{id}?escopo=UNICO\|SEGUINTES` | altera só este ou também os próximos da série |
| `PUT` | `/lancamentos/{id}/realizado` | marca como pago/recebido (só em conta); ao marcar, a data passa a ser a de hoje |
| `PUT` | `/lancamentos/{id}/fatura` | transfere a compra de cartão para a fatura de outro mês (`{ "ano", "mes" }`), normalmente a próxima; aceita até a fatura anterior à da compra, nunca antes, nem compra já paga; não leva para uma fatura anterior que já recebeu pagamento |
| `DELETE` | `/lancamentos/{id}?escopo=UNICO\|SEGUINTES` | apaga só este ou também os próximos (encerra a despesa fixa) |

### `/movimentacoes`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/movimentacoes` | `TRANSFERENCIA` (origem e destino), `DEPOSITO` (destino) ou `SAQUE` (origem) |
| `DELETE` | `/movimentacoes/{id}` | apaga; num pagamento de fatura, é o estorno |

## Regras

- **Saldo da conta** = saldo inicial + receitas realizadas − despesas realizadas + depósitos e transferências recebidas − saques, transferências enviadas e faturas pagas. Lançamento pendente não conta.
- **Saldo Geral** soma as contas ativas marcadas com `somaSaldoGeral`; **Saldo Total** soma todas as contas ativas.
- **Cartão**: a fatura fecha `diasFechamento` dias antes do vencimento. A compra precisa ser **anterior** à data de fechamento para entrar na fatura; no próprio dia já vai para a seguinte. Compra no cartão não mexe em conta: quem debita a conta é o pagamento da fatura, que também marca as compras como realizadas. Uma compra ainda não paga pode ir para uma fatura seguinte ou para a imediatamente anterior, desde que essa não tenha sido paga — na inclusão ou depois; a escolha é mantida mesmo se o lançamento for editado ou o vencimento do cartão mudar.
- **Relatório mensal**: lançamentos em conta pela data; cartão de crédito só pelo total de cada fatura, na data de vencimento. O que já foi pago da fatura conta como realizado.
- **Ciclo mensal**: o "mês" do dashboard e do relatório começa no dia configurado (`diaInicioCiclo`, padrão 1 = mês do calendário) e vai até a véspera dele no mês seguinte. O ciclo leva o nome do mês em que tem mais dias: até o dia 15, o mês em que começa (dia 5: 05/10 a 04/11 é outubro); do 16 em diante, o seguinte (dia 25: 25/09 a 24/10 é outubro). Cada cartão entra com a fatura cujo vencimento cai dentro do ciclo. Mudar o dia não altera nenhum lançamento, só reagrupa todos os meses, inclusive os passados.
- **Despesa fixa** repete todo mês no mesmo dia (dia 31 vira o último dia em meses curtos). As ocorrências são criadas sob demanda, quando o mês é consultado.
- **Parcelado**: o valor informado é o total; a divisão é em centavos e a sobra vai para a primeira parcela.

## Banco de dados

PostgreSQL, com schema versionado por **Flyway** (migrations em `src/main/resources/db/migration`):

- `V001__Inicial.sql`
- `V002__fatura_transferida.sql`
- `V003__configuracoes.sql`

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
