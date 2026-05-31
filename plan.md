# Play Framework — Build to Learn Plan

## Learner Profile

- **Background**: Maths undergrad, AI masters, 3 years as performance analyst analysing HTTP request flows across micro-services
- **Strengths**: Strong Scala (advanced features), functional programming, HTTP semantics (from traffic analysis/auditing side), Python fluency
- **Transitioning to**: Scala dev building micro-services
- **Play experience**: Controllers/routes, conceptual grasp of Action composition via FP knowledge. No custom BodyParsers, Filters, or deep framework internals.
- **Goal**: Understand Play's internals by rebuilding core abstractions from scratch. Not copy-paste — understand *why* the design is the way it is.

---

## Testing Conventions

All tests use **ScalaTest** with `AnyFreeSpecLike` nesting style:

```scala
"MyComponent" - {
  "someMethod" - {
    "returns X when Y" in {
      ...
    }
  }
}
```

### Base Traits

**`UnitSpec`** — for all pure logic tests (no framework, no async). Used for everything in Phases 1, 1.5, and most of Phase 3+:
```scala
trait UnitSpec extends AnyFreeSpecLike with RichMatchers
```

**`AsyncUnitSpec`** — for tests involving `Future[Result]` (Phase 3 onwards):
```scala
trait AsyncUnitSpec extends AnyFreeSpecLike with RichMatchers with ScalaFutures with IntegrationPatience
```

### RichMatchers
Mixed into both base traits. Composes the most useful ScalaTest mixins:
```scala
trait RichMatchers
    extends Matchers
    with Diagrams
    with EitherValues
    with OptionValues
    with TryValues
    with AppendedClues
```
Gives every test `.value` on `Option`/`Either`/`Try`, `shouldBe`, and `withClue` for adding context to failures.

### Table-Driven Tests
For smart constructors and any logic with many input combinations, use `TableDrivenPropertyChecks`:
```scala
val cases = Table(
  ("input",    "expected"),
  ("GET",      Right(Method.GET)),
  ("INVALID",  Left("Unknown HTTP method: INVALID"))
)
forAll(cases) { (input, expected) =>
  Method(input) shouldBe expected
}
```

### Property-Based Tests with ScalaCheck
For invariant testing (e.g. "any Int in 100–599 produces a valid Status"), use **ScalaCheck** via `ScalaTestPropertyChecks`:
```scala
forAll(Gen.choose(100, 599)) { code =>
  Status(code).isRight shouldBe true
}
forAll(Gen.choose(600, 9999)) { code =>
  Status(code).isLeft shouldBe true
}
```
This is stronger than a table — it tests the property for the entire input space, not just chosen examples.

### What Not To Use
The following are Play *application* testing tools — they require a running Play app and are not applicable when building the framework itself:
- `GuiceOneAppPerSuite` / `ComponentSpec`
- `FakeRequest` / `play.api.test.Helpers`
- Jsoup HTML assertions
- WireMock (integration testing, a later concern)

---

## Phase 1: The HTTP Model — `Request`, `Result`, `Handler`

**Goal**: Define the pure data structures that represent an HTTP exchange.

**Key concepts**:
- `RequestHeader` vs `Request[A]` — why the split? Why is the body type generic?
- `Result` (Play's response) — status, headers, body as a byte stream
- Immutability as a design choice

**Design history**:
- Play 1.x was Java-centric and mutable (like Servlet API). Play 2.0 (2012) rewrote everything in Scala with immutable request/response types — a radical departure at the time.
- The generic `A` in `Request[A]` exists because the body hasn't been parsed yet when routing happens. This is a direct consequence of separating header-level concerns (routing, auth checks) from body-level concerns (parsing JSON/form data). This separation enables streaming and backpressure — you don't buffer the whole body just to decide you'll return 404.

**Build**:
- [x] `RequestHeader` (method, uri, path, query params, headers, version)
- [x] `Request[A]` wrapping `RequestHeader` + typed body
- [x] `Result` (status, headers, body)
- [x] `HeaderMap` — case-insensitive header lookup
- [x] Basic HTTP method and status code modelling

**Tests to write**:
- `Headers` — normalisation (stored keys are always lowercase), case-insensitive lookup, multi-value merging when keys collide, `getFirst`, `exists`
- `Status` — ScalaCheck: any Int 100–599 returns `Right`; any Int outside returns `Left`. Named constants have correct underlying codes via `.code`
- `Method` — table-driven: all 7 valid method strings parse correctly; unknown strings return `Left`
- `HttpVersion` — table-driven: all 4 valid version strings parse correctly; unknown strings return `Left`
- `Request[A]` — construction and field access via the `RequestHeader` trait

**Questions to answer by the end**:
- Why does routing only need `RequestHeader` and not `Request[A]`?
- Why is `Result` not generic over its body type the way `Request` is?

---

## Phase 1.5: Mini play-json

**Goal**: Build a small but functional JSON library from scratch, cementing typeclass design and recursive ADTs before they appear in body parsers and Writeable.

**Why here**: `JsValue` and its `Reads`/`Writes` typeclasses are pure data and pure FP — no async, no HTTP concerns. Building them now means Phase 3 body parsers and Phase 6 Writeable can use *our* types rather than an imported library. It also gives focused typeclass practice in a contained problem.

**Why not Guice**: Guice is a Java reflection-based runtime DI framework — reimplementing it teaches Java-style reflection, not Scala. What matters is understanding *why* DI exists and how compile-time DI works (Phase 7). Guice's tradeoffs become obvious once you've done manual wiring.

**Key concepts**:
- `JsValue` as a recursive sealed ADT — models every JSON value type
- `Reads[A]` — `JsValue => JsResult[A]` (can fail, hence `JsResult` not raw `A`)
- `Writes[A]` — `A => JsValue` (cannot fail)
- `Format[A]` — combines both, convenience for symmetric types
- Typeclass instances: where they live, how they compose
- Derived instances: how `Reads[List[A]]` is built from `Reads[A]`

**Design history**:
- play-json was originally part of Play itself, extracted to a standalone library in Play 2.6.
- The `Reads`/`Writes` split (rather than a single `Codec[A]`) was deliberate — not all types need both directions. An incoming webhook payload needs `Reads` but never `Writes`; an audit event needs `Writes` but never `Reads`. `Format[A]` is purely a convenience that extends both.
- `JsResult[A]` (either `JsSuccess[A]` or `JsError`) rather than `Either[Error, A]` was chosen to accumulate *multiple* validation errors rather than fail-fast — important for form validation where you want to report all field errors at once, not just the first.

**Build**:
- [x] `JsValue` sealed ADT — `JsNull`, `JsBoolean`, `JsNumber`, `JsString`, `JsArray`, `JsObject`
- [x] `JsResult[A]` — `JsSuccess[A]` and `JsError` (with accumulated errors as `Seq[(JsPath, String)]`)
- [x] `Reads[A]` typeclass — `def read(json: JsValue): JsResult[A]`
- [x] `Writes[A]` typeclass — `def write(a: A): JsValue`
- [x] `Format[A]` — extends both `Reads[A]` and `Writes[A]`, with auto-derivation via given
- [x] Built-in instances: `Reads[String]`, `Reads[Int]`, `Reads[Boolean]`, `Reads[BigDecimal]`, `Reads[List[A]]`, `Reads[Option[A]]`
- [x] Built-in instances: matching `Writes` for the same types (including `Writes[BigDecimal]`, `Writes[Option[A]]`)
- [x] `(json \ "fieldName")` path syntax — `JsPath` and the `\` operator (both key and index traversal)
- [ ] Macro-derived instances for case classes (or manual, if macro complexity is too much) — deferred to Additional Enhancements

**Tests to write**:
- `JsPath` — path construction, `\` operator traversal on `JsObject`, missing key returns appropriate result
- `JsResult` — `map` on `JsSuccess` transforms value; `map` on `JsError` is a no-op; `flatMap` chains; error accumulation
- `Reads` — built-in instances: `String`, `Int`, `Boolean`, `List[A]`, `Option[A]`; wrong type produces `JsError`
- `Writes` — built-in instances round-trip with matching `Reads`
- `Format` — round-trip property: `reads(writes(a)) == JsSuccess(a)` for all built-in types

**Questions to answer by the end**:
- Why is `Reads[A]` not just `JsValue => Option[A]`? What does `JsResult` buy you over `Option`?
- Why does `Reads[List[A]]` only require a `Reads[A]` instance — how does that instance get found?
- Why is `JsValue` a sealed ADT rather than an enum? (Hint: `JsArray` and `JsObject` contain `JsValue`s)

---

## Phase 2: Testing Infrastructure & Phase 1 / 1.5 Tests

**Goal**: Set up the shared test infrastructure and write tests for all Phase 1 and Phase 1.5 code before building further.

**Why here**: Catching design issues early is cheaper than catching them after four more phases have built on top. This phase also establishes the testing patterns that all future phases will follow.

**Build**:
- [x] `RichMatchers` trait — composes Matchers, Diagrams, EitherValues, OptionValues, TryValues, AppendedClues, ScalaCheckPropertyChecks
- [x] `UnitSpec` base trait — extends `AnyWordSpecLike with RichMatchers`
- [x] Add ScalaCheck dependency to `build.sbt`
- [x] `HeadersSpec` — normalisation, case-insensitive lookup, multi-value merging, `getFirst`, `exists`
- [x] `StatusSpec` — ScalaCheck property-based range validation (100–599), named constants verified
- [x] `MethodSpec` — table-driven: all 7 methods parse; invalid/wrong-case strings rejected
- [x] `HttpVersionSpec` — table-driven: all 4 versions parse; invalid strings rejected
- [x] `JsPathSpec` — `show` rendering, key/index traversal, missing keys, index out of bounds, empty path
- [x] `JsResultSpec` — `map`, `flatMap`, `getOrElse` on both success and error
- [~] `ReadsSpec` — partial: only `Reads[Int]` and `Reads[String]` tested; missing `Boolean`, `BigDecimal`, `List[A]`, `Option[A]`
- [ ] `WritesSpec` — not yet written

**Definition of done**: `sbt test` passes with no failures before moving to Phase 3. Remaining: complete `ReadsSpec` (Boolean, BigDecimal, List, Option) and write `WritesSpec`.

---

## Phase 3: Actions and `EssentialAction`

**Goal**: Understand the function at the core of every Play endpoint.

**Key concepts**:
- `Action[A]` is conceptually `Request[A] => Future[Result]`
- `EssentialAction` is the lower primitive: `RequestHeader => Accumulator[ByteString, Result]`
- `EssentialAction` exists so that the framework can start processing a request *before* the body is fully received
- Action composition — wrapping one action inside another (logging, auth, timing)

**Design history**:
- Play 2.0-2.2 used `Iteratee`-based streaming (influenced by Haskell's iteratee/enumerator pattern). This was powerful but notoriously hard to use.
- Play 2.5 (2016) migrated to Akka Streams, replacing `Iteratee` with `Accumulator` (a thin wrapper over Akka Streams `Sink`). Same design, friendlier API.
- `EssentialAction` survived this migration because its *shape* was right — the underlying stream tech changed but the abstraction held. This is a sign of good design.

**Build**:
- [ ] `Action[A]` — simple function wrapper
- [ ] `EssentialAction` — the streaming-aware version (we'll mock `Accumulator` simply)
- [ ] `ActionBuilder` — the pattern for composing actions (e.g., `AuthenticatedAction`)
- [ ] Demonstrate action composition: logging wrapper, timing wrapper, auth wrapper

**Questions to answer by the end**:
- Why can't you just use `Action` everywhere? What does `EssentialAction` buy you?
- How does action composition relate to function composition in FP?

---

## Phase 4: Body Parsers

**Goal**: Understand how raw bytes become typed request bodies.

**Key concepts**:
- `BodyParser[A]` is `RequestHeader => Accumulator[ByteString, Either[Result, A]]`
- The `Either` is crucial — parsing can fail, and failure produces a `Result` directly (e.g., 400 Bad Request)
- Content-length limits, content-type checking happen here
- Streaming: a body parser doesn't have to buffer everything — it can stream

**Design history**:
- The `Either[Result, A]` return type was a deliberate choice over throwing exceptions. This keeps the error path pure and composable — you can map/flatMap over body parsers.
- Play's default body parser (`AnyContent`) is a content-type dispatcher: JSON for `application/json`, form data for `application/x-www-form-urlencoded`, etc. This is where content negotiation on the *request* side lives.

**Build**:
- [ ] `BodyParser[A]` trait/type
- [ ] `BodyParsers.json` — parse body as JSON (using play-json or circe)
- [ ] `BodyParsers.text` — parse body as raw string
- [ ] `BodyParsers.empty` — ignore the body
- [ ] `BodyParsers.maxLength` — wrap another parser with a size limit
- [ ] Wire body parsers into `Action` so that `Action[JsValue]` actually means something

**Tests to write**:
- `BodyParser.text` — parses bytes to string correctly
- `BodyParser.json` — valid JSON bytes produce `Right(JsValue)`; invalid bytes produce `Left(Result)` with status 400
- `BodyParser.empty` — always produces `Right(())` regardless of input
- `BodyParser.maxLength` — input under limit passes through; input over limit produces `Left(Result)` with status 413

**Questions to answer by the end**:
- Why is a body parser a *function from RequestHeader* rather than just a byte consumer?
- How do body parsers compose with actions?

---

## Phase 5: The Router

**Goal**: Understand how a request finds its handler.

**Key concepts**:
- A router is `PartialFunction[RequestHeader, Handler]`
- Play's `routes` file compiles to a Scala class that implements this partial function
- Path parameters, query parameters, fixed vs dynamic segments
- `Handler` as the general type; `EssentialAction` as the most common handler

**Design history**:
- Play chose a compiled routes file (DSL → code generation) rather than runtime route registration (like Express.js or Flask). Tradeoff: type safety + compile-time checking vs flexibility.
- Play 2.6 introduced `SIRD` (String Interpolation Routing DSL) as a programmatic alternative — `case GET(p"/users/$id") =>` — giving you the best of both worlds.
- The generated router is a `PartialFunction`, which means routers can be composed with `orElse` — this is how sub-routers and module routing work.

**Build**:
- [ ] `Router` as `PartialFunction[RequestHeader, Handler]`
- [ ] Simple DSL for defining routes: `GET / "users" / * => handler`
- [ ] Path parameter extraction
- [ ] Router composition via `orElse`
- [ ] Optional: SIRD-style string interpolation router

**Tests to write**:
- Static routes match correct method + path combinations
- Dynamic path segments are extracted correctly
- Unmatched requests return `None` (partial function not defined)
- Router composition via `orElse` — first router takes priority; second handles its own routes

**Questions to answer by the end**:
- Why is `PartialFunction` a good choice here (vs `Function` returning `Option`)?
- How does router composition enable modular application design?

---

## Phase 6: Filters and the Filter Chain

**Goal**: Understand Play's middleware layer.

**Key concepts**:
- A `Filter` wraps an `EssentialAction` in another `EssentialAction`
- `Filter` vs `EssentialFilter` — one works at the `Action` level, the other at `EssentialAction` level
- The filter chain: request flows through all filters *in order*, response flows back *in reverse*
- This is the onion model (like middleware in Express, WSGI, Ring)

**Design history**:
- Play 2.1 introduced filters. The distinction between `Filter` and `EssentialFilter` exists because `Filter` gives you access to the response body as a stream (so you can transform it), while `EssentialFilter` is simpler.
- Play 2.6 made `EssentialFilter` the default and deprecated `Filter` for most uses — the response-body-transforming use case was rare and the simpler API won out.

**Build**:
- [ ] `EssentialFilter` trait
- [ ] `LoggingFilter` — log request method, URI, status, duration
- [ ] `CORSFilter` — add CORS headers (practical, forces you to think about header manipulation)
- [ ] `AuthFilter` — reject unauthenticated requests before they hit the router
- [ ] Filter chain composition — apply a list of filters to an action

**Tests to write**:
- `LoggingFilter` — result passes through unchanged; logging side-effect occurs
- `AuthFilter` — missing/invalid token short-circuits with 401; valid token passes through
- `CORSFilter` — CORS headers present on response
- Filter chain — filters apply in order; a short-circuiting filter prevents downstream filters running

**Questions to answer by the end**:
- How do filters differ from action composition? When would you use each?
- Why does the filter chain wrap `EssentialAction` rather than `Action`?

---

## Phase 7: Results and Content Negotiation

**Goal**: Understand how responses are constructed and how Play handles content types.

**Key concepts**:
- `Result` builders: `Ok`, `BadRequest`, `NotFound`, `Redirect`, etc.
- `Writeable[A]` — how Play serialises arbitrary types into response bodies
- Content negotiation on the response side: `Accept` header → choose representation
- Sessions, cookies, flash scope — all implemented as headers/cookies under the hood

**Build**:
- [ ] Result builder helpers (`Ok(body)`, `BadRequest`, etc.)
- [ ] `Writeable[A]` typeclass
- [ ] JSON and HTML writeables
- [ ] Session/cookie helpers
- [ ] Content negotiation: `render { case Accepts.Json() => ...; case Accepts.Html() => ... }`

**Tests to write**:
- Result builders produce correct status codes
- `Writeable[String]` and `Writeable[JsValue]` produce correct bytes and content-type headers
- Session/cookie helpers set correct headers on `Result`

**Questions to answer by the end**:
- Why use a typeclass (`Writeable`) rather than requiring all response bodies to extend a trait?
- How does Play's session mechanism differ from traditional server-side sessions?

---

## Phase 8: Wiring It All Together — Application Lifecycle & DI

**Goal**: Understand how a Play application bootstraps and how components are wired.

**Key concepts**:
- `Application` — the top-level object holding router, config, filters, etc.
- `ApplicationLoader` — the entry point that constructs an `Application`
- Guice (runtime DI) vs compile-time DI (`MacWire` / manual wiring)
- Component lifecycle: `onStart`, `onStop`

**Design history**:
- Play 2.0-2.3 used global state (`GlobalSettings`, `Play.current`). This was widely criticized.
- Play 2.4 (2015) introduced dependency injection, initially with Guice. This was controversial in the Scala community (Guice is runtime/reflection-based, very un-Scala).
- Play 2.4+ also supports compile-time DI via `ApplicationLoader` — more idiomatic Scala, no reflection, but more boilerplate.
- The community is split; Lightbend defaults to Guice, but many Scala teams prefer compile-time DI.

**Build**:
- [ ] `Application` case class holding all the pieces
- [ ] Simple compile-time wiring (manual DI — no framework needed)
- [ ] Application lifecycle hooks
- [ ] Wire everything: filters → router → actions → body parsers → results

---

## Milestone: A Working Mini-Play

By the end, we should have a small but functional HTTP framework where:
1. A request comes in as bytes
2. Filters process it
3. The router dispatches to a handler
4. A body parser turns bytes into a typed body
5. An action produces a `Future[Result]`
6. The result is serialised back to bytes

We won't build a real HTTP server (that's Netty/Akka HTTP territory), but we'll have the full logical pipeline that sits between "bytes in" and "bytes out".

---

## Additional Enhancements

Optional improvements that would strengthen the project but add complexity not needed for the core learning goals. Revisit these once all 8 phases are complete.

### Phase 1.5 — Mini play-json

**Macro-derived `Reads`/`Writes` for case classes**
In real play-json, `Json.reads[MyCase]` and `Json.writes[MyCase]` use Scala macros (or Scala 3 `Mirror`-based derivation) to automatically generate typeclass instances for any case class without boilerplate. Implementing this would demonstrate Scala 3's `derives` mechanism and `Mirror` typeclass. Skipped for now because: (a) it's not needed for the body parsers or Writeable, and (b) Scala 3 macro/Mirror APIs are complex enough to warrant their own focused session.

*Suggested approach when revisited*: Use `scala.deriving.Mirror` and recursive given derivation rather than a macro. Play-json uses macros for historical reasons (Scala 2); the idiomatic Scala 3 approach is Mirror-based.

---

## Progress Tracker

| Phase | Status | Notes |
|-------|--------|-------|
| 1. HTTP Model | ✅ Complete | `RequestHeader` (trait), `Request[A]`, `Result`, `Headers`, `Method` (enum), `HttpVersion` (enum), `Status` (opaque type). All use Scala 3 features. |
| 1.5. Mini play-json | ✅ Complete | `JsValue` (sealed trait ADT), `JsPath` (key+index traversal), `JsResult` (covariant, with map/flatMap/getOrElse), `Reads[T]`, `Writes[T]`, `Format[A]` (auto-derived). Built-in instances for Int, String, Boolean, BigDecimal, List[A], Option[A]. Macro-derived case class instances deferred. |
| 2. Testing Infrastructure | 🔄 Mostly complete | `RichMatchers`, `UnitSpec` (AnyWordSpecLike-based) done. All Phase 1 specs complete. Phase 1.5 specs mostly done — `ReadsSpec` partial (Int/String only, missing Boolean/BigDecimal/List/Option), `WritesSpec` not yet written. |
| 3. Actions & EssentialAction | ⬜ Not started | |
| 4. Body Parsers | ⬜ Not started | |
| 5. Router | ⬜ Not started | |
| 6. Filters | ⬜ Not started | |
| 7. Results & Writeable | ⬜ Not started | |
| 8. App Lifecycle & DI | ⬜ Not started | |
