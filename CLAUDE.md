# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Sésame** — kiosk app for the SuperQuinquin entry desk that lets the welcome staff verify a cooperator's status (à jour / en alerte / suspendu / désinscrit), with search and detail screens. The backend is read-only against the production Odoo instance.

## Stack

- **Backend**: Quarkus 3.35 (Java 21, `quarkus-rest` + `quarkus-rest-jackson` + `quarkus-smallrye-openapi`). JSON-RPC client to Odoo hand-written on `java.net.http.HttpClient` (no Odoo extension).
- **Frontend**: Vite + Vue 3 + TypeScript in `src/main/webui/`, served in dev and packaged in prod by **Quinoa**.
- **API client**: **orval** generates a typed `fetch`-based TS client from the OpenAPI spec. Don't hand-edit anything under `src/main/webui/src/api/generated.ts` or `src/main/webui/src/api/model/` — both are git-ignored and regenerated on every front build.
- **Credentials**: `.env` at repo root (loaded automatically by SmallRye Config). `.env` is git-ignored. See `application.properties` for `ODOO_URL`/`ODOO_DATABASE`/`ODOO_LOGIN`/`ODOO_PASSWORD`.

## Commands

| Task | Command |
|---|---|
| Dev (Quarkus + Vite live reload + orval on Vite startup) | `./mvnw quarkus:dev` |
| Package (writes OpenAPI, runs orval, builds Vite bundle, builds jar) | `./mvnw package` |
| Full backend test suite | `./mvnw test` |
| Single test class | `./mvnw -Dtest=MemberSearchTest test` |
| Single test method | `./mvnw -Dtest=MemberSearchTest#orphanUnsubscribedRecordsAreFilteredOut test` |
| Front-only regenerate client + build | `cd src/main/webui && npm run build` |
| Front-only regenerate orval client | `cd src/main/webui && npm run gen:api` |

`./mvnw quarkus:dev` and `./mvnw package` both dump the OpenAPI contract to `src/main/webui/openapi/openapi.{json,yaml}` (via `quarkus.smallrye-openapi.store-schema-directory`). Orval reads the JSON file — **Quarkus does not need to be running** when the front builds.

## Testing discipline — ATDD only

- **No unit tests.** Only behavioural scenarios via `@QuarkusTest` + REST-assured, hitting the HTTP boundary.
- **Red first, always.** Write the failing scenario before any implementation. Confirm it fails for the right reason, then make it pass with the minimum needed.
- Odoo is stubbed in tests by `WireMockOdooResource` (a `QuarkusTestResourceLifecycleManager`) plus the `OdooStub` helper. Use `OdooStub.stubSearchReadMatching(model, bodyFragment, records)` to disambiguate multiple calls in the same scenario (e.g. the member lookup vs. the binôme lookup) — match on distinctive fragments like `"\"id\",\"=\",1247"` vs. `"\"parent_id\",\"=\",1247"`.
- Quinoa is auto-disabled in the `test` profile (`%test.quarkus.quinoa.enabled=false`) so test boot stays fast.

## Architecture

```
Browser  ──┐                                  ┌── Odoo JSON-RPC (.env-driven)
           │  fetch /api/members*             │     • res.partner search_read/read only
           ▼                                  │     • login uid cached in OdooClient
  Vue (src/main/webui/src/screens)            │
   └─ orval client (api/generated.ts) ──┐    │
                                        ▼    │
                              Quarkus /api/members
                              MemberResource → MemberRepository → OdooClient
```

Two endpoints, both in `MemberResource`:
- `GET /api/members?q=…` → `MemberRepository.search(q)` — name OR cooperator number, fuzzy `ilike`.
- `GET /api/members/{id}` → `MemberRepository.findById(id)` — does a second `search_read` to expand the binôme (either the `is_associated_people` child or, if the queried record IS the child, the parent member).

The frontend has two screens (`SearchScreen.vue` / `DetailScreen.vue`) plus shared components in `src/main/webui/src/components/`. Navigation is purely component-state (no router): `App.vue` holds `selectedId` and swaps between the two.

## Odoo data conventions

These are non-obvious and load-bearing — read `MemberRepository.java` before changing any of them.

- **Cooperator number** displayed to the user (the "N°") is `res.partner.barcode_base`, **not** the Odoo internal `id`. Both are exposed in DTOs (`id` is used for routing, `number` for display).
- **Name** is stored as `"LASTNAME, Firstname"` (uppercase surname, comma-separated). `splitName` parses it; surnames are recapitalised to title case.
- **Status mapping** Odoo `cooperative_state` → app `MemberStatus`:
  - `up_to_date` / `not_concerned` / `exempted` / `vacation` → `ok`
  - `alert` / `delay` → `alert`
  - `suspended` / `blocked` / `unpayed` → `suspended`
  - `unsubscribed` → `removed`
- **Binôme** A titulaire member's binôme is the `res.partner` whose `parent_id` points at them and has `is_associated_people=true`. Conversely, if the record IS the binôme child, the binôme is its `parent_id`.
- **Orphan-désinscrit filter** A person who used to be a titulaire and is now a binôme of someone else has **two** records in `res.partner`: the old `is_member=true, cooperative_state=unsubscribed, parent_id=false` ghost and the current `is_associated_people=true` record. Search drops the ghosts via both an Odoo domain clause and a client-side `isOrphanGhost` check (`unsubscribed AND !is_associated_people AND parent_member_num == 0`). On the production DB this hides ~240 duplicates.

## Inspecting Odoo

Use the `odoo-query` skill (read-only — `search_read` / `read` / `fields_get` only, never write). It loads `.env` and shells out to `curl` against the JSON-RPC endpoint. Run it before designing a new endpoint to confirm the exact field names and selection values on `res.partner` (or `shift.shift`, `shift.registration`, etc.).
