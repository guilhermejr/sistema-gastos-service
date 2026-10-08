# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Personal finance control — categories, accounts, credit cards, income/expense entries, transfers and monthly reports.

| | |
|---|---|
| Port | `9008` |
| Context path | `/gastos-service` |
| Role required | `ROLE_GASTOS` |

Part of a personal microservices system; sibling repos live at `../sistema-*`. The API gateway fronts it at `https://sistema-backend.guilhermejr.net/gastos-service`. The frontend's "Gastos" tab is its only client.

## The money model

Read these together before changing any calculation:

- **Account balance is computed, never stored.** `SaldoService`: `saldoInicial` + realized income − realized expenses on the account + deposits/incoming transfers − withdrawals/outgoing transfers/invoice payments. A pending (`realizado = false`) entry does not count. Movements (`movimentacoes`) count from the moment they are recorded, even with a future date.
- **Card entries never touch an account.** A `Lancamento` has either `conta` or `cartao` (DB check constraint). On a card, `realizado` is not user-controlled: `PUT /lancamentos/{id}/realizado` refuses it. Paying the invoice (`FaturaService.pagar`) creates one `PAGAMENTO_FATURA` movement that debits the account and marks the invoice's pending entries `realizado`, linking them through `pagamento_id`. Deleting that movement (`DELETE /movimentacoes/{id}`) is the reversal: entries go back to pending.
- **An entry paid on an invoice is frozen.** Editing or deleting it is refused until the payment is reversed — otherwise the account debit and the invoice would disagree.
- **An invoice is not a table.** It is the card's entries whose `fatura` (due date) falls in a month, plus the payments for that month. Lookups use the **month** (`fatura BETWEEN first AND last day`), not the exact day, so entries keep showing in the right invoice after the card's due day changes. Changing due day or closing days on a card re-computes `fatura` for its **pending** entries only.
- **Closing rule** (`CalendarioUtil.vencimentoDaCompra`): closing date = due date − `diasFechamento`. A purchase must be **before** the closing date to enter that invoice; buying on the closing day already goes to the next one. Due day 31 falls back to the last day of shorter months. `CalendarioUtilTest` pins these cases — run it after touching the util.
- **Moving a purchase to another invoice** (`PUT /lancamentos/{id}/fatura`, usually "next invoice", or `fatura: { ano, mes }` on `POST /lancamentos`) sets `fatura` by hand and `fatura_transferida = true`, through `CartaoService.transferirParaFatura`. Any month from **one before** the natural invoice onwards is accepted — the previous one covers a bank that closed later than the card's configured cycle; earlier months are refused, and so is moving an entry already paid. Moving to an invoice **earlier than where the entry is now** is also refused when that invoice already has a payment (`CartaoService.faturaPaga`: any `PAGAMENTO_FATURA` for that card and month, even partial) — the payment debited what the invoice held and the entry would be left out; reversing the payment lifts the block. Moving forward never checks payments. `FaturaResponse.anteriorPaga` tells the frontend whether the previous month is paid. On `POST`, an installment purchase shifts every installment by the same number of months; a fixed series shifts only its first occurrence. Every place that re-positions a card entry goes through `CartaoService.posicionarNaFatura`, which keeps a transferred entry in its chosen month (at the card's current due day) as long as that month is still accepted for the purchase's natural invoice; a date change that leaves it behind, or switching to another card, resets it. Moving back to the natural month clears the flag. Don't set `fatura` directly in new code — call `posicionarNaFatura` or `transferirParaFatura`.
- **The monthly report counts cards by invoice, not by purchase** (`RelatorioService`). `lancamentos` carries only account entries, by their date; each card contributes one `faturas` item — the invoice whose due date falls in the month, on that date, with its net total. In the totals the invoice's paid part is realized and its pending part is pending. Card purchases never appear one by one there, and card refunds are already netted into the invoice. Movements never count in the totals; the response carries the cycle's `TRANSFERENCIA` movements separately in `transferencias`, for display (and undo, via `DELETE /movimentacoes/{id}`) only. The dashboard's monthly Receitas/Despesas cards use the same rule: both go through `RelatorioService.calcular`, so the cards and the report's Entradas/Saídas always match. Change the rule there, not in either caller.
- **"The month" is a cycle** (`util/Ciclo`). Dashboard, report and account statement don't use the calendar month: they use the cycle starting on the user's `configuracoes.dia_inicio_ciclo` (1–28, no row = 1 = calendar month) and ending the day before it next month. `ano`/`mes` in requests and responses is the cycle's **name**: up to day 15 the month it starts in, from 16 the next one — a fixed cut, so every month has exactly one cycle (counting days would make short Februaries skip a name). Each card contributes the invoice whose due date (at its current due day) falls inside the cycle — `Ciclo.mesDaFatura`, then looked up by month as everywhere else. Generate fixed occurrences up to `RelatorioService.ateOndeGerar` before calculating. Changing the day rewrites no row, but regroups every month, past ones included.
- **Invoice status** is derived on read: `ABERTA` before closing, then `PAGA` if nothing pending, `VENCIDA` after the due date, else `FECHADA`.

## Fixed (recurring) entries are generated lazily

A `FIXA` entry creates a `Recorrencia` (the template) plus the first occurrence. Further occurrences are **not** created up front — the series has no end. Every read that looks at a period calls `RecorrenciaService.gerarAte(usuario, date)` first:

- dashboard → end of next month (so "próximos a pagar/receber" see the next occurrence);
- report → end of the requested month;
- invoice → end of the invoice month.

`recorrencias.gerado_ate` is the date of the last occurrence created; generation only walks forward from it. Consequences:

- **Deleting one occurrence is permanent** — it is behind `gerado_ate` and never regenerated.
- `findParaGerar` takes a **pessimistic write lock**: the dashboard and the report may ask for the same month concurrently, and without the lock both would insert the same occurrence. Callers must be `@Transactional` (the generator methods are `Propagation.MANDATORY` to enforce it).
- `escopo=SEGUINTES` on `PUT /lancamentos/{id}` rewrites the template and the following pending occurrences (value, day, category, account/card). On `DELETE` it ends the series (`ativo = false`) and deletes the following pending occurrences.

Installments (`PARCELADA`) are all created at once, one per month on the same day, sharing a `parcelamento` UUID. The total is split in cents with the remainder on the **first** installment, so the sum is exact. `escopo=SEGUINTES` on an installment propagates description, category and account/card — not value or date.

## Deactivation rules

Accounts, cards and categories are deactivated, not deleted (categories may be deleted only while unused). Inactive items can't receive new entries, but editing an existing entry that already points to one is allowed.

- **Account**: refused while its balance is non-zero, while it is the payment account of an active card, or while it has an active fixed series. Inactive accounts are left out of Saldo Geral/Total — the zero-balance rule is what keeps money from silently vanishing from the totals.
- **Card**: refused while it has an active fixed series. A deactivated card leaves the dashboard card list, but its unpaid invoices stay in "próximos a pagar" and can still be paid.

## Every lookup by id is scoped to the owner

`*DoUsuario` methods in each service go through `findByIdAndUsuario`. A record belonging to someone else answers **404, not 403**. Category, account and card ids in a request body go through the same lookups, so an entry cannot point at another user's account.

## Request/response conventions

- Money **in** is a BR string (`"1.234,56"`, `@ValorMonetario`); `saldoInicial` accepts a leading `-`. Money **out** is a JSON number.
- Dates **in** are `dd/MM/yyyy` (`@DataBrasil`); dates **out** are ISO `yyyy-MM-dd`.
- `ModelMapperConfig` uses **STRICT** matching: responses nest several objects with same-named fields (`conta.nome`, `cartao.nome`) and the default strategy guesses between them. STRICT does not flatten `recorrencia.id` → `recorrenciaId` on its own, hence the explicit mapping in `LancamentoMapper`.
- "Today" comes from the `Clock` bean (`America/Bahia`), not the machine's timezone.

## Database migrations

PostgreSQL with Flyway, migrations in `src/main/resources/db/migration`.

`spring-boot-flyway` is declared in `pom.xml` and **must stay there**. In Spring Boot 4 the Flyway auto-configuration moved into that separate module; with only `flyway-core` on the classpath the service starts normally, logs nothing, and applies no migrations at all.

## Security comes from a library

There is no `config/security` package here. JWT validation, the filter, the security chains and `@EnableMethodSecurity` arrive from `net.guilhermejr.sistema:seguranca-jwt` through auto-configuration. Removing or failing to resolve that dependency does not break the build — the service starts with Spring Boot's default HTTP Basic lock-down instead. `@PreAuthorize` only works because the library brings `@EnableMethodSecurity`.

## Configuration comes from outside

`application.yml` only bootstraps `spring.config.import`:

- **Vault** — `secret/application` (shared) and `secret/gastos-service` (`eurekaHostname` plus `gastosDB*` credentials)
- **Config Server** — `gastos-service/gastos-service.yml` in the config repo: port, context path, datasource

`VAULT_TOKEN` is required and has no default; without it the failure surfaces much later as a misleading `${...} is malformed`.

## Building and running

Java **21 only** (ModelMapper's ByteBuddy breaks on JDK 24+):

```bash
export JAVA_HOME=/Users/guilhermejr/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home
./mvnw clean package -DskipTests
VAULT_TOKEN=<token> java -jar target/gastos-service-*.jar --spring.profiles.active=dev
```

`./mvnw test` runs the unit tests (no Spring context, no infrastructure needed).

## Deploying

`git push origin main` **is** the deploy, through the VPS `post-receive` hook, like the sibling services. The `Dockerfile` only copies a prebuilt jar and has a `HEALTHCHECK` on `/actuator/health`.
