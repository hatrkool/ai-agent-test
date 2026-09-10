# CLAUDE.md — IT Services Info Agent

Guidance for Claude Code (or any agent) working in this repository. This
project is a take-home exercise (`ai-developer-task.md`, Estonian) being
built strictly to `IMPLEMENTATION_PLAN.md`. That plan is the source of
truth for scope, contracts and phasing — this file exists to keep an agent
from drifting off it mid-session, and to record house rules that aren't in
the plan.

**Read order for a fresh session:** `NEXT_SESSION.md` (exact resume point,
if present) → `IMPLEMENTATION_PLAN.md` (full spec) → `ai-developer-task.md`
(original Estonian brief, only if a plan detail is ambiguous).

## What this is

A Spring Boot + Spring AI service: a Estonian-language internal IT-helpdesk
FAQ agent that answers only from a static Markdown knowledge base, cites
its sources, and refuses rather than hallucinates. The whole exercise is
graded on architecture, Spring AI/tool-calling correctness, agent
groundedness, security (prompt-injection resistance), tests, and docs —
**not** on feature breadth. Do not add scope beyond §§1–11 of the plan.

## Non-negotiable technology decisions

Do not re-litigate these mid-build (plan §1):

- Java 21, Spring Boot 4.x, **Spring AI 2.0.1** (GA 2026-06-12).
- Gradle with committed Wrapper, Kotlin DSL.
- OpenAI `gpt-4.1-mini` by default, model name and `temperature=0.0`
  configurable via properties (never hardcoded).
- `SimpleVectorStore` in-memory — no external vector DB.
- Package root `ee.example.itagent`.

### Spring AI 2.0 API shape — verify, don't assume

Most tutorials online (and most model pretraining) reflect Spring AI 1.x.
The 2.0 shape that actually applies here:

- Tool execution is a `ToolCallingAdvisor` in the `ChatClient` advisor
  chain, not inside `ChatModel`. Use `ChatClient`.
- `toolNames()` / `SpringBeanToolCallbackResolver` are gone. Register tools
  as `ToolCallback` beans, pass explicitly via `.tools(...)`.
- No `.options` segment in config keys: `spring.ai.openai.chat.model`, not
  `spring.ai.openai.chat.options.model`.
- Jackson 3: only `@JsonValue`/`@JsonProperty` keep the old
  `com.fasterxml.jackson.annotation` package; other Jackson annotations
  move to `tools.jackson.databind.annotation`.
- `SimpleVectorStore.builder(embeddingModel).build()`, `Document.builder()`,
  `SearchRequest.builder()...similarityThreshold()`, and
  `vectorStore.similaritySearch(request)` are the confirmed 2.0.1 shapes.

Before writing code against a Spring AI 2.0 API you haven't already
verified in this session, check `https://docs.spring.io/spring-ai/reference/`
(or the module source/javadoc) rather than pattern-matching from memory or
older docs. If something in the plan turns out wrong at build time, follow
the reference docs and **record the deviation in the README** — do not
silently downgrade to 1.1.x.

## Architecture invariants (do not weaken these)

These are the mechanisms the whole exercise is graded on. Changing them
needs a documented reason in the README, not a silent shortcut.

1. **Citations are verified in code, not trusted from the model.** Every
   tool call result is appended to a request-scoped `RetrievalLedger`.
   `OutputGuard` checks every `SourceRef` and excerpt in the model's
   structured output against that ledger after the model runs. A source or
   excerpt the ledger didn't produce → refuse with `NO_SOURCE_MATCH`. Never
   relax this to "trust the model said it came from X."
2. **`getDocument` never touches the filesystem at request time.** It's a
   map lookup built once at startup (`Map<String, KnowledgeDocument>`).
   This is the entire SEC-06 (path traversal) defense — by construction,
   not by sanitizing input. Do not add a `Path`/`File` construction on the
   request path for this tool.
3. **Guard pipeline order is fixed** (plan §8): Bean Validation →
   RateLimitFilter → `InputGuard` (pre-LLM, regex-based, refuses before any
   model call) → `SensitiveDataScrubber` (redact/refuse before anything
   reaches OpenAI) → `ChatClient` + tools → `OutputGuard` (post-LLM,
   5 checks) → response. Security behavior must stay deterministic — don't
   move a check that's currently pre-model to rely on the model's judgment.
4. **KB sensitive-data scan runs at startup and fails the boot**, naming
   the file and detector but never the matched value, if any KB document
   contains something matching `SensitiveDataPatterns`. This is already
   implemented in `KnowledgeBaseLoader`; new KB files must pass it, not
   bypass it.
5. **`refused == false` ⟹ `sources` non-empty and `answer` contains at
   least one `[allikas: <file>]` marker** whose file is in `sources`. This
   is the core "does not hallucinate" invariant (`OutputGuard`). UC-05
   (topic listing) and UC-07 (ambiguous question → clarification refusal)
   are the two documented exceptions — see plan §3 for how they're handled;
   don't invent a third exception without documenting it the same way.
6. **Only three tools exist**: `listTopics`, `searchKnowledgeBase`,
   `getDocument`. No general-purpose tool, no filesystem/network/shell
   access from any tool. Keep the allowlist unit test (asserts exactly
   these three `ToolCallback` bean names) green — it's the enforcement
   mechanism for "agent can only do what tools allow."
7. **Never log full question text at INFO or above.** `SafeLogging` logs
   `sessionId`, a SHA-256 prefix of the question, its length, and matched
   pattern *labels* only. Full-text logging is DEBUG-only, gated behind
   `agent.logging.verbose` (default off). Same rule for sensitive-data
   matches: log the detector label and count, never the matched value.
8. **System prompt lives in `src/main/resources/prompts/system-prompt.txt`**,
   loaded via `@Value("classpath:...")`. Never inline it as a Java string
   literal — this is a deliberate design choice for reviewable diffs.

## Coding conventions

- DTOs are Java records (plan §3). `Confidence` serializes lowercase via
  `@JsonValue`. Keep enums and records exactly as specified unless a
  compile-time API constraint forces a change — document any such change.
- Prefer constructor injection; keep guard/scrub/retrieval components as
  small single-purpose classes matching the package layout in plan §2
  (`api`, `agent`, `kb`, `tools`, `guard`, `config`). Don't collapse them
  into a god class for convenience.
- No comments explaining *what* code does. A short comment is fine only
  for a non-obvious invariant (e.g. why `getDocument` must stay a map
  lookup, why excerpts are matched after whitespace normalization).
- Don't add abstractions, config flags, or "future-proofing" beyond what
  the plan specifies — this is a scoped exercise, not a production system,
  and over-engineering it is graded against you as much as under-building.
- Unit tests (`src/test`) must never make a live OpenAI call — mock
  `ChatClient`/`ChatModel`/`EmbeddingModel`. `@WebMvcTest` slices that
  don't load the AI beans at all are the preferred way to prove "model
  never called" for guard/validation tests.
- Integration tests (`src/integrationTest`) assert **behavior**, never
  exact model wording (plan §10). Every test method name or Javadoc must
  carry its scenario ID (`UC-01`, `SEC-04`, `API-04`, ...) — graders map
  tests to requirements by that ID, so don't drop it.
- KB Markdown files: Estonian only, fictional data only, YAML front-matter
  with `title`/`topic`/`aliases`, 3–6 `##` sections per file. Before adding
  a new document or alias, check it doesn't lexically bleed into another
  document's matches — see the alias-collision gotcha in `NEXT_SESSION.md`
  (literal folded-phrase substring containment, not stemmed token overlap,
  and don't let a general/overview doc mention a system that has its own
  dedicated doc).

## Workflow rules for this repo

- **Ask before touching the local toolchain or environment** — installing
  Java/Gradle, running version-check commands, finishing the Gradle
  wrapper setup, or running any build/test for the first time in a
  session. This was explicit user instruction after a prior session did
  this speculatively.
- **Ask before any destructive or environment-altering command.**
- Windows/PowerShell is the primary shell here. Prefer `Invoke-WebRequest`
  over `curl` for downloads (the user's `curl` availability is unreliable
  even via Git Bash's mingw64).
- Follow the plan's phase structure (plan §12): finish a phase's "Done
  when" condition before starting the next, and land each phase as its own
  commit using the commit message given in the plan for that phase.
- After finishing a phase, ask whether to continue to the next phase or to
  pause and write a handoff (update `NEXT_SESSION.md` / memory) for a
  fresh session. Proactively warn if the context window is getting full
  rather than pushing through silently.
- Before trusting any Spring AI API usage written in a prior session
  without a working build, compile it first — `NEXT_SESSION.md` notes that
  earlier API choices were verified against live docs, not a compiler.

## Build and test commands

```bash
./gradlew test              # unit tests, no network, always must be green
./gradlew integrationTest   # live-model tests, needs OPENAI_API_KEY
./gradlew checkstyleMain    # style/lint check, config at config/checkstyle/checkstyle.xml
```

Checkstyle is wired into `check`/`build` (`maxWarnings = 0`, failures are not
ignored) — a violation fails the build, not just a warning. The ruleset is
deliberately lean: no Javadoc requirements (this project defaults to no
comments, see above), no import-order rules — it catches real defects
(unused imports, empty catch blocks, missing braces, equals/hashCode pairs)
and basic consistency (120-char lines, brace placement), not house style.
Don't add stricter modules (Javadoc, naming conventions, cyclomatic
complexity) without checking they don't fight the conventions above.

Unit report: `build/reports/tests/test/index.html`.
Integration report: `build/reports/tests/integrationTest/index.html`.
Both are gitignored — never commit them.

Run the full integration suite **three consecutive times** before
declaring Phase 5 done (plan §10 stability rule) — a single green run
against a live LLM doesn't count as passing.

## Where to look for more detail

- Full requirements matrix (all UC-*/SEC-*/API-* scenarios): plan §10.
- Exact guard regexes and sensitive-data detector patterns: plan §8.
- CI workflow shape: plan §11.
- Known limitations to state honestly in the README: plan §13 — don't hide
  these, naming them is graded positively.
