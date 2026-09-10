# Handoff — continue IT Services Info Agent build

This file is the resume point for a fresh Claude Code session. Read
`CLAUDE.md` first (project-specific rules and invariants), then
`IMPLEMENTATION_PLAN.md` for the full spec. This file only tracks
*progress against that plan*, not the plan itself.

## Status: Phase 5 complete (of 6), committed pending, live-verified 3x.

Phase 4's commit (`b4bab71`) is the last one actually landed in git. Phase 5's
work (below) is done and verified but **not yet committed** — see "Not yet
committed" at the bottom before doing anything destructive.

### Commit history so far

```
b4bab71 feat: agent orchestration with pre- and post-model guards      (Phase 4)
af4b4ba docs: update handoff notes after Phase 3
6744c6c feat: knowledge base tools with allowlist and retrieval ledger (Phase 3)
44b9553 build: Gradle wrapper, Checkstyle, and a CLAUDE.md project guide
822c257 feat: knowledge base loading and hybrid retrieval              (Phase 2)
2582a26 feat: project skeleton, API contract and input validation      (Phase 1)
```

### Done

**Phases 1–4:** unchanged from the Phase 4 handoff, see git history and
CLAUDE.md. All still solid — Phase 5 did not touch the guard classes'
logic, only `Confidence` (bug fix), `KnowledgeBaseTools`' `getDocument`
description (prompt-engineering aid), and `system-prompt.txt`.

**Phase 5 — integration tests and prompt tuning (complete, live-verified):**

- `src/integrationTest/java/ee/example/itagent/integration/` — 4 files:
  `AbstractIntegrationTest` (shared `@SpringBootTest(RANDOM_PORT)` +
  `TestRestTemplate` setup, rate-limit disabled via `@TestPropertySource`),
  `UseCaseIntegrationTest` (UC-01..UC-13, 13 tests), `SecurityIntegrationTest`
  (SEC-01/02/03/04/05/06/08, 7 tests — SEC-07 stays unit-only), `ApiIntegrationTest`
  (API-04, 1 test). **21 tests total**, matching the plan's scenario count.
- New Gradle dependency: `org.springframework.boot:spring-boot-resttestclient`
  on `integrationTestImplementation` — see gotcha #1 below.
- `./gradlew integrationTest` run **three consecutive times** (forced with
  `--rerun` for runs 2–3, since Gradle caches a green result and silently
  skips re-execution otherwise — see gotcha #4), all three fully green,
  21/21 each time. Plan §10's stability rule is satisfied.
- **Two real product bugs found and fixed** via live testing before the
  test suite was even written (see gotchas #2–3): a `Confidence` enum
  deserialization bug that 500'd every real request, and a missing
  `@EnableConfigurationProperties`-style gap — no wait, that was Phase 4.
  Phase 5's two real bugs were the `Confidence` case-sensitivity bug and
  the test-suite's own shared-session design bug (below).
- **One real test-design bug found and fixed by me, not the app:** almost
  every single-turn integration test defaulted to `sessionId = null`,
  which `AgentService` maps to the literal string `"anonymous"` — meaning
  20+ unrelated questions (GitLab, Kubernetes, injection attempts,
  off-topic, sensitive-data) were all piling into *one* shared chat-memory
  conversation, contaminating the model's context. Fixed in
  `AbstractIntegrationTest.ask(String)`: generates a fresh UUID session
  per call now. This single fix took the first full-suite run from 7
  failures down to 2.
- System prompt tuned through several live rounds (all still in
  `system-prompt.txt`, see gotcha #5 for the one still-open gap):
  - Added a rule forcing the model to reformulate follow-up search queries
    with topic context (fixed UC-06's first failure mode).
  - Added a **mandatory tool re-invocation rule**, promoted to rule 1 for
    prominence: the model must call a tool in *every* message, even a
    follow-up whose answer seems already known from conversation history,
    because `OutputGuard`'s `RetrievalLedger` is `@RequestScope` — a prior
    turn's tool result is invisible to this turn's verification. Includes
    a worked example and an explanation of *why* (the ledger resets per
    request). Fixed UC-06 reliably; helped but did not fully fix UC-13
    (gotcha #5).
  - Added a rule refusing to graft a retrieved (real, correctly-cited)
    passage onto a question naming a system/entity that isn't actually in
    that passage — fixes a genuine hallucination-adjacent bug where the
    model answered a fictional "Mars server access" question by silently
    substituting "Marsi serverile" into the real GitLab procedure text
    (UC-12). The excerpt was real and ledger-verified; the *answer* was
    not honest about what the KB actually said. Not a code bug — `OutputGuard`
    can't detect topical mismatch, only grounding, by design (CLAUDE.md
    invariant 1 is about citation verification, not relevance judgment).
- `KnowledgeBaseTools.getDocument`'s `@Tool` description was strengthened
  to explicitly invite reuse for re-confirming an already-mentioned
  document — a tool-selection-level nudge, tried after system-prompt-level
  nudges alone weren't fully sufficient for UC-13.

### Gotchas found and fixed this session (Spring Boot 4.1.1, live-model behavior)

1. **`TestRestTemplate` moved to a bizarrely-named new artifact:
   `spring-boot-resttestclient`** (`org.springframework.boot.resttestclient.
   TestRestTemplate`, `org.springframework.boot.resttestclient.autoconfigure.
   AutoConfigureTestRestTemplate`, needs the `@AutoConfigureTestRestTemplate`
   annotation now too — it's no longer auto-provided by `@SpringBootTest
   (webEnvironment = RANDOM_PORT)` alone). Guessed wrong twice first
   (`spring-boot-restclient-test`, which is actually for `@RestClientTest`
   mock-client slices — a different, easily-confused artifact) before
   downloading Maven Central's actual `org/springframework/boot/` directory
   listing via `curl` and finding it by name search. **Lesson recorded for
   next time: when `javap`-ing a guessed artifact comes up empty, don't
   guess a second name — list the actual Maven Central directory.**
2. **`Confidence` enum 500'd every real request at first.** Jackson 3's
   record/enum deserialization enforces the `@JsonValue`-annotated form
   strictly — the model's structured output returned `"HIGH"` (matching the
   JSON-schema-visible raw enum constant name) but the converter only
   accepted `"high"` (the `@JsonValue` lowercase form), throwing
   `InvalidFormatException` on every single non-refused response. Found via
   the live UC-01 check (not caught by any unit test, since unit tests mock
   the model and never exercise the real converter round-trip). **Fixed**
   by adding a `@JsonCreator` factory (`com.fasterxml.jackson.annotation.
   JsonCreator` — confirmed as the only location this annotation exists on
   this classpath, despite CLAUDE.md's note that "other" Jackson
   annotations move to `tools.jackson.databind.annotation` in Jackson 3;
   empirically only `@JsonValue`/`@JsonProperty`/`@JsonCreator` stayed in
   the old package, nothing moved to replace them) doing case-insensitive
   `valueOf`.
3. **Shared "anonymous" chat-memory session across unrelated integration
   tests** — a test-design bug, not an app bug, but worth recording
   prominently: see "Done" above. If a future session adds more
   integration tests, always route through `ask(String)` (auto-generates a
   session) or pass an explicit unique session — never pass `null`/omit it
   intentionally expecting isolation.
4. **`./gradlew integrationTest` is cached like any other `Test` task** —
   a second invocation with no source changes reports `UP-TO-DATE` and
   does *not* re-hit the live model. Use `--rerun` (not `--rerun-tasks`,
   both work but `--rerun` is the modern flag) for each of the plan's
   required 3 consecutive runs after the first.
5. **UC-13 residual limitation, accepted and documented rather than
   chased further:** "Kust see info pärineb?" (a pure provenance
   follow-up) triggers a fresh tool call far less reliably than every
   other follow-up phrasing tested (observed 0/4 in isolated debug-logged
   runs, across a mandatory rule, a worked example, a "why" explanation,
   and a tool-description nudge — four distinct prompt-engineering
   attempts). Root cause understood, not mysterious: the model can already
   see `[allikas: gitlab-access.md]` in its own prior message and
   reasonably (from its perspective) treats that as sufficient without
   understanding that our verification ledger resets every request.
   **Decision (confirmed with the user):** rather than keep prompt-tuning
   an apparent ~0%-compliance case, `UseCaseIntegrationTest
   .uc13_sourceInquiry_groundedIfPossible_neverFabricatedIfNot` now
   asserts the *actual* guaranteed property — grounded citation if the
   model does re-verify, a clean refusal with **no fabricated source** if
   it doesn't — rather than hard-requiring the ideal path every time. This
   is not a weaker safety guarantee, just an honest test of what the
   architecture actually promises. **Say this plainly in the README's
   known-limitations section** (plan §13 already anticipates "LLM may err"
   as an acceptable, gradable-positively limitation to name).

### Design decisions this session (don't re-litigate later)

- Retry policy across the suite: plan §10 says "one retry per test at
  most." `uc05` and `uc06` use exactly one retry (genuine occasional
  flakiness — passed in isolation every time tested, just not always on
  the first attempt within a full-suite run). `uc13` uses zero retries now
  that its assertion accepts either honest outcome — retrying no longer
  makes sense once "safe refusal" is itself a pass.
- `AbstractIntegrationTest` disables rate limiting via `@TestPropertySource
  (properties = "agent.rate-limit.enabled=false")`. Without this,
  `RateLimitFilter` (always pulled into the real embedded-server context,
  unlike a `@WebMvcTest` slice) would make suite pass/fail depend on how
  many scenarios ran in the last 60 seconds rather than on each scenario's
  actual behavior. `RateLimitFilterTest` (unit) covers the real rate-limit
  behavior in isolation.
- Confirmed live, no rate-limit/infrastructure errors anywhere in any
  DEBUG-logged run (grepped for `429`, `rate.?limit`, `ERROR`, `WARN` —
  none found tied to any failure). Every failure traced to a specific,
  understood cause (session sharing, the `Confidence` bug, or genuine
  model tool-recall variance) — worth remembering before assuming
  "flaky integration test" means "infrastructure," in this or future
  sessions.

### Environment / workflow notes carried forward

- `.env` at the repo root has a real `OPENAI_API_KEY` (gitignored, never
  committed). Load it into a shell before any live command:
  `set -a && source .env && set +a` (bash) — confirmed working pattern
  this session, used for both `bootRun` and `integrationTest`.
- **User confirmed:** OK to use the `.env` key for Phase 5's live testing;
  this was asked explicitly before any billed API usage began, per
  CLAUDE.md's "ask before running the app live" rule.
- Manual `bootRun` debugging pattern used repeatedly this session: start
  in background (`./gradlew bootRun --console=plain > logfile 2>&1 &`
  via a backgrounded Bash call), poll `/api/v1/health` in a bounded loop
  until 200, find its PID via `wmic process where "name='java.exe'" get
  ProcessId,CommandLine /format:list` (grep for `ItAgentApplication`),
  `taskkill //PID <pid> //F` to stop. Watch for **stale instances**
  left over from a prior turn still holding port 8080 — caused one
  confusing false-positive "UP after 1s" early this session; always
  verify the PID and a genuine `Started ItAgentApplication` log line,
  not just a 200 from `/health`, when in doubt.
- Windows/Git Bash `curl` mangles non-ASCII (Estonian ä/õ/ü/ö) command-line
  arguments — this is the exact gotcha CLAUDE.md already warned about.
  **Fixed pattern:** write the JSON body to a UTF-8 file (via the Write
  tool) and use `curl --data-binary @file.json`, never `-d "..."` with
  Estonian text inline.

### Not yet committed

Everything Phase 5 touched is uncommitted:
`build.gradle.kts` (new dependency), `src/main/java/.../Confidence.java`
(bug fix), `src/main/java/.../KnowledgeBaseTools.java` (tool description),
`src/main/resources/prompts/system-prompt.txt` (multiple rounds of
tuning), `src/integrationTest/` (new, 4 files), this file. A fresh session
should confirm with the user before committing (per CLAUDE.md workflow
rules) — suggested Phase 5 commit message is the plan's own:
`test: integration coverage for all UC and SEC scenarios` — though
consider whether the `Confidence` bug fix and `getDocument` description
change belong in that commit or split out, since they're product fixes
discovered *during* Phase 5 rather than test code per se. Ask the user.

## Next steps in order (Phase 6 — final phase)

1. `.github/workflows/ci.yml` per plan §11: unit-test job (always runs),
   integration-test job (needs `push` event + `OPENAI_API_KEY` secret
   present, gated via the `steps.guard.outputs.has_key` pattern in the
   plan), both upload their HTML report as a workflow artifact.
2. `README.md` — plan §12 Phase 6 spells out exactly what it must contain:
   purpose/scope; versions; local run instructions incl. env vars; `curl`
   examples for a success and a refusal; architecture list/diagram;
   security model (all 5 guard layers + the documented SEC-04/injection
   policy choices already made in Phase 4); ID→test mapping; HTML report
   paths (`build/reports/tests/test/index.html` and
   `.../integrationTest/index.html`); data-handling paragraph (what's
   logged, what's sent to OpenAI, KB is fictional-only); known
   limitations — **use plan §13's list verbatim as a starting point, plus
   the UC-13 tool-recall gotcha from this session** (gotcha #5 above) —
   that one is genuinely new and specific to this build, not in the
   plan's generic list.
3. `SUMMARY.md` — one page max: architecture, security mechanisms with
   justification, known limitations. Reuse README content, condensed.
4. Tidy commit history if needed (plan grades "mõistlik commit'ide
   ajalugu" — reasonable commit history) — decide with the user whether
   Phase 5's uncommitted work becomes one commit or is split (see "Not
   yet committed" above).
5. **Done when:** a clean clone, `./gradlew test`, and the documented run
   command work with nothing but the README as guidance (plan's own
   Phase 6 bar).
6. **Commit:** `docs: README, summary and CI workflow`.

After Phase 6, the build is complete per the plan's 6-phase structure —
the next thing after that is the actual submission (plan's "Tarnitavad
tulemused": repo link, README, one-page summary, link to a CI run with
the HTML report artifact — the last of which needs an actual push to a
GitHub remote, which hasn't been discussed with the user yet).

## Prompt to paste into a new session

```
You are the best software architect and developer in the world, specializing in AI development.
Continue the IT Services Info Agent build in c:\net\code\ai-test-smit\ai-agent-test.
Read CLAUDE.md first, then NEXT_SESSION.md for exact status (Phase 5 of 6
complete and live-verified 3x, but NOT YET COMMITTED — check with the user
about the commit before anything else) and the ordered Phase 6 next steps.
Then read IMPLEMENTATION_PLAN.md for the full spec. Start Phase 6: CI
workflow, README.md, SUMMARY.md, per plan §12's Phase 6 checklist and
§13's known-limitations list (plus the UC-13 tool-recall limitation
recorded in this file's gotcha #5, which is specific to this build and
not in the plan).
This phase is mostly documentation, not new code, so live OpenAI calls
should not be needed — but if anything requires running the app, ask
first per CLAUDE.md's rules, same as every prior session.
Ask before creating git commits — Phase 5's work is uncommitted and
should probably be resolved (committed, and how it's split) before
starting Phase 6's own commit.
```
