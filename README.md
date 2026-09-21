# Receipt Processing API

A small HTTP API that takes an uploaded receipt, runs it through a (stubbed) OCR + extraction
pipeline, creates a transaction with structured tax lines, auto-itemizes it into line items, and
reconciles the items against the receipt total — flagging anything that doesn't add up instead of
silently forcing the numbers to match.

## Tech stack

- Java 21
- Spring Boot 3.3 (Spring Web, Spring Data JPA, Validation)
- Maven (with wrapper — no local Maven install required)
- H2 (in-memory)
- JUnit 5 + AssertJ + MockMvc

## Run

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. No environment variables, external services, or API
keys are required (see [OCR](#ocr) below).

## Test

```bash
./mvnw clean test
```

## API

### `POST /receipts` — upload a receipt

Multipart upload. Returns the new receipt's ID.

```bash
curl -F "file=@fixtures/task-a/receipt-clean.txt" http://localhost:8080/receipts
# -> {"receiptId":1}
```

### `POST /receipts/{id}/process` — run OCR + extraction, create/update the transaction

```bash
curl -X POST http://localhost:8080/receipts/1/process
```

Returns the full transaction (merchant, date, currency, grand total, taxes, line items,
`itemizeStatus`). Calling this again on the same receipt **updates the same transaction** — see
[Reprocessing](#reprocessing).

### `GET /transactions/{id}` — retrieve a transaction

```bash
curl http://localhost:8080/transactions/1
```

```json
{
  "id": 1,
  "receiptId": 1,
  "merchant": "Cafe Mitte",
  "date": "2026-03-12",
  "currency": "EUR",
  "grandTotal": 17.85,
  "itemizeStatus": "COMPLETE",
  "taxes": [{ "id": 1, "name": "VAT", "rate": 0.19, "amount": 2.85, "jurisdiction": null }],
  "lineItems": [
    { "id": 1, "description": "Espresso", "amount": 3.50, "taxAmount": null, "quantity": null }
  ]
}
```

### `POST /transactions/{id}/itemize` — re-run auto-itemize from stored OCR

Re-parses the receipt's already-persisted raw OCR text and replaces line items only. Does not call
OCR again, does not require re-uploading the receipt, and does not touch merchant/date/currency/
grand total/taxes/transaction ID.

```bash
curl -X POST http://localhost:8080/transactions/1/itemize
```

### `PATCH /transactions/{id}/items` — user override (edit / merge / split)

See [PATCH](#patch) below for the contract.

```bash
curl -X PATCH http://localhost:8080/transactions/1/items \
  -H "Content-Type: application/json" \
  -d '{
        "items": [
          { "description": "Espresso", "amount": "3.50" },
          { "description": "Sandwich (half)", "amount": "4.45" },
          { "description": "Sandwich (half)", "amount": "4.45" },
          { "description": "Mineral water", "amount": "2.60" }
        ]
      }'
```

If the proposed items don't reconcile with the transaction's total and stored taxes, this returns
**409** instead of persisting anything:

```bash
curl -i -X PATCH http://localhost:8080/transactions/1/items \
  -H "Content-Type: application/json" \
  -d '{"items":[{"description":"Made up","amount":"1.00"}]}'
```

```json
{
  "error": "RECONCILIATION_CONFLICT",
  "message": "Proposed line items do not reconcile with the transaction total and stored taxes",
  "expectedTotal": 17.85,
  "itemsTotal": 1.00,
  "taxTotal": 2.85,
  "calculatedTotal": 3.85,
  "difference": 14.00
}
```

### `GET /health`

```bash
curl http://localhost:8080/health
# -> {"status":"UP"}
```

## Processing flow

```
upload (POST /receipts)
  -> OCR stub reads the uploaded file's own text back
  -> raw OCR text persisted on the Receipt
  -> deterministic extraction (merchant/date/currency/total/taxes/line items)
  -> find-or-create Transaction for this receipt
  -> persist taxes, persist line items
  -> reconcile items + taxes against grand total
  -> set itemizeStatus (COMPLETE / NEEDS_REVIEW)
```

## Reconciliation

**Rule:** `sum(lineItem.amount) + sum(tax.amount)` compared to `grandTotal` using
`BigDecimal.compareTo()` at a normalized scale of 2 — **no epsilon tolerance**. Line item amounts
are net (pre-tax), matching the fixtures' own "Subtotal" lines.

- Items exist and reconcile exactly → `COMPLETE`.
- No reliable items were extracted, or the items don't reconcile → `NEEDS_REVIEW`.
- The service **never** invents a balancing line item and **never** adjusts the grand total to
  force a match — a mismatch always surfaces as `NEEDS_REVIEW` (on `process`/`itemize`) or
  HTTP 409 (on `PATCH`), with the original numbers untouched.

Money is always `BigDecimal`, never `double`/`float`. All business-equality checks use
`compareTo()`, never `equals()`, since `BigDecimal("2.0").equals(BigDecimal("2.00"))` is `false`
even though the two represent the same amount.

## Reprocessing

A receipt has **at most one** transaction (`Receipt 1 → Transaction 0..1`), enforced by a unique
`receipt_id` foreign key on the `transactions` table. Calling `POST /receipts/{id}/process`
multiple times finds and updates that same transaction — the transaction ID never changes, and no
duplicate is ever created. Its taxes and line items are replaced (old rows removed, new rows
inserted), not accumulated.

## PATCH

`PATCH /transactions/{id}/items` uses a **desired-state** contract: the request body is the
complete list of line items that should exist after the call, not a diff or an operation code.
Editing, merging, and splitting are all just different shapes of that same list — there's no
separate `op=merge`/`op=split`, and no JSON Patch (RFC 6902). The proposed list is validated
against the transaction's existing total and stored taxes **before** any persisted item is
touched: on a mismatch, the database is left completely unchanged and the endpoint returns 409.

## OCR

OCR is **intentionally stubbed and fully deterministic** — no external OCR/LLM API, and no API key
is needed to run this project. The three assignment fixtures under `fixtures/task-a/*.txt` already
represent OCR-equivalent plain text, so "running OCR" is simply reading the uploaded file's own
bytes back as text (`TextFileOcrProvider`). Upload one of those fixture files as the `file` field
on `POST /receipts` to exercise the full pipeline. The `OcrProvider` interface is the seam where a
real vendor could be substituted later without touching the processing service.

## Out of scope

- Auth, UI, and production file storage (uploads go to local disk, path `./data/receipts` by
  default) — matches the assignment's stated scope.
- Only the plain-text fixture format is parsed; there is no general-purpose OCR/layout engine.
- No pagination/listing endpoints (`GET /receipts`, `GET /transactions`) — not required by the
  brief.
- Uploads are capped at 20MB (`spring.servlet.multipart.max-file-size`); exceeding it returns 413.
