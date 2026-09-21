# Architecture

## Component flow

```
Controller  ->  Service            ->  Repository (Spring Data JPA)
(thin,          (business rules,
 DTOs only)      transaction
                 boundaries)
```

`ReceiptController` / `TransactionController` do request/response mapping only. Business logic
lives in three services:

- **`ReceiptService`** — upload + local file storage (filename sanitized to a bare basename before
  writing, so a path-traversal-style client filename can't escape the storage directory).
- **`ReceiptProcessingService`** — orchestrates `POST /receipts/{id}/process`: `OcrProvider` ->
  raw text persisted -> `ReceiptExtractor` -> find-or-create `Transaction` -> replace
  taxes/items -> `ReconciliationService` -> status.
- **`TransactionService`** — `GET`, re-itemize, and the PATCH override, reusing the same
  `ReceiptExtractor`/`ReconciliationService` collaborators.

`OcrProvider` and `ReceiptExtractor` are interfaces so the processing services never depend on how
OCR/parsing actually happens; `ReconciliationService` is a pure, side-effect-free component
(sum + compare, no I/O) so it's trivially unit-testable and is the single place the reconciliation
rule lives.

## Entity relationships

```
Receipt (1) ----owned by unique FK---- (0..1) Transaction (1) --*-- Tax
                                                     |
                                                     *
                                                  LineItem
```

`Transaction` is the owning side of the `Receipt 1 → Transaction 0..1` relationship: a unique,
non-null `receipt_id` foreign key is what actually prevents a second transaction from ever being
created for the same receipt (not just JPA-level cardinality). `Tax` and `LineItem` are
`@OneToMany` with `cascade = ALL, orphanRemoval = true` on `Transaction`; replacing them is a
`clear()` + re-`add()` on the *existing* managed collection (never reassigning a new `List`), which
is what lets Hibernate's dirty checking turn a "replace" into real deletes + inserts.

## OCR / parser boundary

`OcrProvider.extractRawText(Receipt)` returns raw text; `ReceiptExtractor.extract(String)` turns
that text into structured fields. Neither knows about the other, and the processing service only
depends on the interfaces — swapping the stub for a real OCR/LLM vendor touches one class.

## Reconciliation

`sum(lineItems) + sum(taxes)` vs. `grandTotal`, compared with `BigDecimal.compareTo()` at a fixed
scale of 2, no tolerance. Same rule, same service, used by `process`, `itemize`, and the PATCH
validation — never duplicated.

## Atomicity

`process()`, `reItemize()`, and `updateItems()` are each a single `@Transactional` method.
`updateItems()` specifically computes the proposed items' reconciliation from request DTOs
*before* calling `replaceLineItems` on the managed `Transaction` — if it doesn't reconcile, the
method throws before touching any managed entity, so there is no dirty state for Hibernate to
flush and nothing to roll back. Only a reconciling PATCH ever reaches `replaceLineItems`.
