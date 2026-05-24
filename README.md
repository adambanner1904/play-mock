# play-mock

A from-scratch implementation of the [Play Framework](https://www.playframework.com/)'s core abstractions, built as a learning project to understand *why* Play is designed the way it is — not just how to use it.

> **Status**: Work in progress. Phases 1 and 1.5 complete. See [plan.md](plan.md) for the full roadmap.

---

## Motivation

Most framework tutorials teach you the API. This project takes the opposite approach: rebuild the internals from first principles so the design decisions become obvious. Every type here exists because Play has an equivalent, and every design choice has a reason rooted in the history of the framework.

---

## Architecture

A Play application is a pipeline that transforms an HTTP request into an HTTP response:

```
bytes in
  → Filters (cross-cutting concerns: logging, auth, CORS)
    → Router (PartialFunction[RequestHeader, Handler])
      → Body Parser (bytes → typed body A)
        → Action (Request[A] → Future[Result])
          → Result (status + headers + body bytes)
bytes out
```

Each layer is a distinct abstraction with a clear type signature. The pipeline is composable at every level.

---

## What's Built

### `playmock.http` — The HTTP Model

Core immutable data types representing an HTTP exchange.

| Type | Description |
|------|-------------|
| `RequestHeader` | Trait — method, URI, path, query params, headers, HTTP version |
| `Request[A]` | Extends `RequestHeader`, adds typed body `A` (parsed by a `BodyParser`) |
| `Result` | HTTP response — `Status`, `Headers`, body bytes |
| `Headers` | Case-insensitive, multi-value header map with smart constructor |
| `Method` | Enum — `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` |
| `HttpVersion` | Enum — `HTTP1_0`, `HTTP1_1`, `HTTP2`, `HTTP3` with wire-format parsing |
| `Status` | Opaque type wrapping `Int` — range-validated, named constants for common codes |

**Key design decisions:**
- `Request[A]` is generic because routing happens before body parsing — you can reject a request on headers alone without consuming potentially large body bytes
- `Result` is not generic — `Writeable[A]` consumes the type at construction time; the framework only ever needs bytes
- `Headers` normalises keys at construction (smart constructor) — case-insensitivity is an invariant, not a lookup-time concern
- `Status` uses an opaque type — distinct from `Int` at compile time, zero runtime overhead

### `playmock.json` — Mini play-json

A small JSON library built from scratch to practice typeclass design before it appears in body parsers and `Writeable`.

| Type | Description |
|------|-------------|
| `JsValue` | Sealed ADT — `JsNull`, `JsBoolean`, `JsNumber`, `JsString`, `JsArray`, `JsObject` |
| `JsPath` | Path through a JSON tree — built with `__  \ "field" \ 0`, traverses with `path(json)` |
| `JsResult[+A]` | Covariant sealed trait — `JsSuccess[A]` or `JsError` (accumulated errors) |
| `Reads[A]` | Typeclass — `JsValue => JsResult[A]`. Built-in instances for primitives and collections |
| `Writes[A]` | Typeclass — `A => JsValue`. Cannot fail by design |
| `Format[A]` | Extends both `Reads[A]` and `Writes[A]` — for symmetric types |

**Key design decisions:**
- `JsValue` is a sealed trait hierarchy, not an enum — `JsArray` and `JsObject` are recursive and need companion objects with their own behaviour
- `JsResult` uses accumulated errors (not fail-fast `Either`) — all validation failures are reported at once
- `JsError extends JsResult[Nothing]` — covariance means it's a valid `JsResult[A]` for any `A` without casting
- `Reads[List[A]]` composes from `Reads[A]` via `given [A: Reads]: Reads[List[A]]` — typeclass composition in practice

### `playmock.action` — Actions *(coming in Phase 3)*

`Action[A]` and `EssentialAction` — the function at the core of every endpoint.

### `playmock.parser` — Body Parsers *(coming in Phase 4)*

`BodyParser[A]` — how raw bytes become a typed `Request[A]`.

### `playmock.routing` — Router *(coming in Phase 5)*

`Router` as `PartialFunction[RequestHeader, Handler]`, composable via `orElse`.

### `playmock.filter` — Filters *(coming in Phase 6)*

`EssentialFilter` — the middleware layer wrapping `EssentialAction`.

### `playmock.result` — Result Builders *(coming in Phase 7)*

`Ok(body)`, `BadRequest`, `Writeable[A]` typeclass, content negotiation.

### `playmock.app` — Application Lifecycle *(coming in Phase 8)*

Compile-time DI, `ApplicationLoader`, wiring the full pipeline.

---

## Running the Tests

```bash
sbt test
```

Tests use ScalaTest `AnyFreeSpecLike` with `UnitSpec` / `RichMatchers` base traits. Property-based tests use ScalaCheck.

---

## What This Is Not

- A production HTTP framework
- A replacement for Play — this is a learning tool
- A real HTTP server — there is no Netty/Akka HTTP integration (that's below the abstraction boundary this project targets)

The goal is to own the logical pipeline between "bytes in" and "bytes out", not the I/O layer beneath it.

---

## Tech Stack

- Scala 3.3.4 (LTS)
- sbt 1.12.8
- ScalaTest 3.2.18
- ScalaCheck (via scalatestplus)
- Apache Pekko (added in Phase 3 for `ByteString` / `Accumulator`)

---

## Learning Plan

See [plan.md](plan.md) for the full phase-by-phase learning roadmap, including design history notes, build checklists, and test specifications for each phase.
