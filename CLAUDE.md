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
- **Default destination of a new expense** (`configuracoes.despesa_conta_id` / `despesa_cartao_id`, at most one — DB check). Only the frontend uses it, to preselect the account/card. Saving requires it active and owned (`contaAtivaDoUsuario`/`cartaoAtivoDoUsuario`); deactivating it later keeps the choice stored, and the frontend falls back to its automatic choice while it is inactive. `PUT /configuracoes` replaces every field, so a client must send the current values of the ones it isn't changing.
- **Invoice status** is derived on read: `ABERTA` before closing, then `PAGA` if nothing pending, `VENCIDA` after the due date, else `FECHADA`.

## Card transactions from the bank (Open Finance, via Pluggy)

A card can be linked to a credit card at the bank (`PUT /cartoes/{id}/banco { bancoContaId }`; empty unlinks). `BancoService.sincronizar` (button `POST /cartoes/{id}/banco/sincronizar`, and `BancoAgendamento` daily at 08:00, **`prod` profile only**) reads every transaction of that card from the Pluggy API (`PluggyClient`, read-only) and keeps those of invoices from `cartoes.banco_inicio_fatura` on in `transacoes_banco`. Each sync stamps `cartoes.banco_sincronizado` (UTC) and stores the card limit from the Pluggy account's `creditData` (`banco_limite`, `banco_limite_disponivel`; used is the difference). All three are cleared when the link changes and returned in `CartaoResponse` and in the sync response. It **never creates or changes an entry** — the table is for reconciliation, which is the next step (`situacao`: `A_REVISAR` → `CONCILIADA`/`IMPORTADA`/`IGNORADA`).

- **Re-running is idempotent**: `banco_id` (Pluggy's id) is unique; a stored row is updated in place (a pending purchase can change), and a row the bank stopped reporting is deleted only while still `A_REVISAR`.
- **No date filter on the fetch.** Future installments come with the date they will be charged; filtering up to today drops them. Each installment is one bank transaction with `installmentNumber/totalInstallments`; the user's own installment entries count only the *remaining* ones (bank 5/11 = system 1/7).
- **Invoice month** is the bank's `billForecastDate`, falling back to the due date of the bill in `billId`; `transacoes_banco.fatura` stores it at the card's due day, like `lancamentos.fatura`.
- **Dates are converted to `America/Bahia`**: the bank sends UTC, and a purchase at 22:37 would otherwise land on the next day.
- **Pagination is by cursor** (`GET /v2/transactions`; the page-based `/transactions` answers 410). The `after` value is base64 with `+ / =`: it is taken out of `next` and sent as a URI variable, never pasted raw.
- **Only one user has it.** The Pluggy credentials belong to one person, so everything is gated by `pluggyUsuario` (that user's id): for anyone else `GET /banco/cartoes` answers an empty list and the other endpoints refuse. Missing Vault keys don't break startup — the integration just stays off.
- **Reconciliation suggestions** (`GET /cartoes/{id}/faturas/{ano}/{mes}/conciliacao`, `ConciliacaoService` + `util/Conciliador`) are computed on every read, never stored. Per bank row still to review: `IGNORAR` (a credit starting with "PAGAMENTO" — the previous invoice's payment), `CONCILIAR` (same type, same cents and same number of **remaining** installments; equal-sized groups pair by date), `ESCOLHER` (group sizes differ), `VALOR_DIFERENTE` (single purchase within max(R$ 1, 5%) and 5 days — 10% wrongly paired a 68,40 purchase with a 61,80 lunch) or `CRIAR`. The **last installment counts as a single purchase**: when one installment is left the user enters it without installments (bank 10/10 = system single). `ConciliadorTest` pins cases from the real Nov/2026 invoice. Entries already linked (`transacoes_banco.lancamento_id`, unique, `ON DELETE SET NULL`) are left out; a reviewed row whose entry was deleted is treated as to-review again.
- **Reconciliation actions** (`ConciliacaoService`, each answers the invoice's updated `ConciliacaoResponse`): `POST /cartoes/{id}/banco/transacoes/{tid}/conciliar { lancamentoId, corrigirValor }` links to an entry of the same card, invoice and type that no other row links (`corrigirValor` rewrites that one entry through `LancamentoService.atualizar` with `Escopo.UNICO` — not the next fixed occurrences, and refused on an installment); `…/criar { descricao, categoriaId }` creates the entry through `LancamentoService.inserir` in the bank's invoice (`fatura: { ano, mes }`) — an installment N/M becomes M−N+1 installments, the last one a single purchase, matching how the user enters old purchases — and links the installment in that invoice (`IMPORTADA`); `…/ignorar`; `…/desfazer` (back to `A_REVISAR`; an entry created from the bank stays); `POST /cartoes/{id}/faturas/{ano}/{mes}/conciliacao/confirmar { itens: [{ transacaoId, lancamentoId|null }] }` links or ignores many at once, all or nothing. `categoriaSugeridaId` on a `CRIAR` row is the category of the last linked entry whose bank row had the same `categoriaBanco`.
- Linking starts at the card's **current** invoice (`FaturaService.mesDaFaturaAtual`): earlier invoices were entered without the bank and are not reconciled. Re-linking or unlinking is refused once any row was reviewed.

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

- **Vault** — `secret/application` (shared) and `secret/gastos-service` (`eurekaHostname`, `gastosDB*` credentials and, optionally, `pluggyClientID`/`pluggySecretID`/`pluggyItemId`/`pluggyUsuario` for the bank integration)
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
