# Handoff — continue IT Services Info Agent build

This file is the resume point for a fresh Claude Code session. Read
`CLAUDE.md` first (project-specific rules and invariants), then
`IMPLEMENTATION_PLAN.md` for the full spec. This file only tracks
*progress against that plan*, not the plan itself.

## Status: Phase 3 complete (of 6), committed, fully compiler-verified.

Everything below has actually been built with `./gradlew`, not just
written against docs. The Gradle wrapper works, `./gradlew clean check`
(tests + Checkstyle) is green, and a full `@SpringBootTest` context load
passes with no `OPENAI_API_KEY` set.

### Commit history so far

```
09ded24 docs: task brief, implementation plan and session handoff notes
2582a26 feat: project skeleton, API contract and input validation      (Phase 1)
822c257 feat: knowledge base loading and hybrid retrieval              (Phase 2)
44b9553 build: Gradle wrapper, Checkstyle, and a CLAUDE.md project guide
6744c6c feat: knowledge base tools with allowlist and retrieval ledger (Phase 3)
```

### Done

**Phase 1 — skeleton (complete, compiler-verified):** as before, plus one
real bug found and fixed this session — see "Gotcha" below.

**Phase 2 — KB and retrieval (complete, compiler-verified):** unchanged
from before, now passing `./gradlew test` for real.

**Phase 3 — tools and ledger (complete, compiler-verified):**
- `tools/KnowledgeBaseTools` — the three `@Tool` methods (`listTopics`,
  `searchKnowledgeBase`, `getDocument`), registered via
  `MethodToolCallbackProvider.builder().toolObjects(...).build()` in
  `tools/ToolConfig` (single `@Bean List<ToolCallback>`). `getDocument`
  is a pure map lookup on `KnowledgeBaseLoader.documentsByFile()` — no
  `Path`/`File` ever constructed, which is the SEC-06 defense.
- `tools/RetrievalLedger` — `@RequestScope` bean; every tool call appends
  the `KnowledgeChunk`(s) it returned. `listTopics` records one synthetic
  chunk per document (`id = file + "#topics"`, `text = doc.title()`) so
  UC-05's "sources = one SourceRef per listed doc, excerpt = title line"
  design (plan §3) is ledger-verifiable later.
- `KnowledgeBaseToolsTest` (6 tests): allowlist is exactly 3 named tools;
  traversal input (`../../../etc/passwd`) → not-found, no exception;
  unknown file → not-found; known file → content + ledger populated;
  direct `searchKnowledgeBase` call → ledger populated; `listTopics` → 7
  docs + 7 ledger entries.
- `ItAgentApplicationTests` — added beyond the plan's file list: a plain
  `@SpringBootTest` context-loads smoke test. Not in §2's layout, but
  cheap and valuable — it's the only test that actually assembles the
  full bean graph (KB index, tool config, request-scoped ledger, OpenAI
  autoconfiguration) instead of testing classes in isolation. Passes
  with no `OPENAI_API_KEY`.

The `AgentController` still returns a hard-coded refusal — nothing calls
`ChatClient` anywhere yet. That's Phase 4.

### Not started yet

Phase 4 (agent orchestration, `system-prompt.txt`,
`InputGuard`/`InjectionPatterns`, `SensitiveDataScrubber` request-time
half, `OutputGuard`, `SafeLogging`, `RateLimitFilter`, `AgentProperties`),
Phase 5 (integration tests, prompt tuning), Phase 6 (CI workflow, README,
SUMMARY.md).

## Environment status (resolved this session — do not re-derive)

- **Gradle wrapper works.** `./gradlew test`, `./gradlew checkstyleMain
  checkstyleTest`, `./gradlew check`, `./gradlew clean build -x
  integrationTest` all pass. Wrapper is Gradle 9.7.1, committed.
- **Java 21.0.12.1 is installed** at
  `C:\Program Files\Java\jdk-21.0.12.1`. `javap.exe` lives there too —
  useful for inspecting jars in `~/.gradle/caches/modules-2/...` when a
  Spring AI 2.0 API needs verifying against the actual class file rather
  than docs (docs can lag; the jar is ground truth).
- **Git identity is configured** (`user.name`/`user.email` set locally
  by the user this session) — commits work now.
- **Checkstyle is wired in and clean.** `config/checkstyle/checkstyle.xml`,
  `maxWarnings = 0`, part of `check`/`build`. No Javadoc requirement —
  see CLAUDE.md.

## Gotcha found and fixed this session — Spring Boot 4.1.1 moved `@WebMvcTest`

The Phase 1 test classes (`AgentControllerApiTest`, `HealthControllerTest`)
were written against Spring Boot 3.x's package for `@WebMvcTest`
(`org.springframework.boot.test.autoconfigure.web.servlet`) and failed to
compile: `spring-boot-test-autoconfigure:4.1.1`'s jar only contains
`jdbc`/`json` packages now — verified by listing the actual jar contents,
not by guessing. Confirmed via Spring's own 4.1.1 API docs
(docs.spring.io) that Boot 4 split test-slice annotations into dedicated
artifacts: `@WebMvcTest` moved to
`org.springframework.boot.webmvc.test.autoconfigure`, and needs
`org.springframework.boot:spring-boot-webmvc-test` on the test classpath
(not pulled in by `spring-boot-starter-test` alone anymore).

**Fixed:** added the dependency in `build.gradle.kts`, updated both test
files' imports. Both are committed in the Phase 1 commit.

**Implication for Phase 5/6:** other Boot 3→4 test-slice moves are
plausible (`@DataJpaTest`, `@RestClientTest`, etc. — not used here, so
not blocking) — if a future test annotation doesn't resolve, check the
actual jar contents or docs.spring.io for the 4.1.x package, don't assume
the 3.x location. Worth a line in the README's "known limitations" or
API-notes section in Phase 6, documenting this as a real deviation
encountered during the build (the plan explicitly rewards documenting
such deviations rather than hiding them).

## Gotcha already fixed (from the prior session) — KB alias collision

Unchanged from before: `KnowledgeBaseIndex` alias matching uses literal
folded-phrase substring containment (`foldedQuery.contains(foldedAlias)`),
not stemmed token-set overlap, to avoid false ties like
`access-requests.md` matching almost as strongly as `gitlab-access.md`
for GitLab questions. See `KnowledgeBaseIndex.search()` if tuning scoring
in Phase 5 — let `KnowledgeBaseIndexTest` / `KnowledgeBaseToolsTest` catch
regressions rather than re-deriving this by hand.

## Environment gotchas for this machine (Windows, PowerShell primary)

- No reliable `curl` for the user — use PowerShell's `Invoke-WebRequest`
  for downloads.
- **Ask before touching the toolchain/environment or running
  destructive commands** — this was honored this session (asked before
  checking versions, finishing the wrapper, and running the first
  build). Continue doing so in Phase 4+, especially before running
  `bootRun` or anything that starts a long-lived process.
- **Ask before creating git commits** unless already mid-approved for a
  specific batch — also honored this session (commit structure was
  confirmed via AskUserQuestion before committing).

## Next steps in order (Phase 4)

1. `prompts/system-prompt.txt` (Estonian, plan §7), loaded via
   `@Value("classpath:...")` — never inline as a Java string literal.
2. `agent/AgentPromptFactory`, `agent/AgentService` — wire `ChatClient`
   (autoconfigured by `spring-ai-starter-model-openai`) with
   `.defaultToolCallbacks(...)` using the `List<ToolCallback>` bean from
   `tools/ToolConfig` (already built, Phase 3), chat memory keyed on
   `sessionId`, structured output into `AgentResponse`.
   **Not yet verified this session:** the Spring AI 2.0.1 chat-memory API
   shape (`ChatMemory`/`MessageWindowChatMemory`/advisor wiring). Check
   the actual jar (`spring-ai-model`/`spring-ai-client-chat`) via `javap`
   the way the tool-calling API was verified in Phase 3, don't assume the
   1.x or remembered shape.
3. `guard/InputGuard` + `guard/InjectionPatterns` (plan §8's literal
   regex list, both languages) — pre-LLM, deterministic refuse.
4. `guard/SensitiveDataScrubber` (request-time half) — **reuse**
   `guard/SensitiveDataPatterns` (already built in Phase 2 for the KB
   startup scan; read its actual method signatures before reusing, don't
   assume). Add the `SENSITIVE_DATA_IN_INPUT` refusal path.
5. `guard/OutputGuard` — the 5 checks from plan §8, reading
   `tools/RetrievalLedger` (already built and ready to consume).
6. `guard/SafeLogging`, `config/RateLimitFilter` (needs `bucket4j-core`
   added to `build.gradle.kts` — not yet added), `config/AgentProperties`.
7. Replace `AgentController`'s hard-coded placeholder body with a real
   call into `AgentService`.
8. Done when: unit tests cover every guard rule; a manual `curl`/
   `Invoke-WebRequest` against the running app answers UC-01 correctly
   with a valid citation; a manual SEC-01 attempt refuses without any
   model call. (Running the app live needs `OPENAI_API_KEY` and starts a
   process — ask first.)

## Prompt to paste into a new session

```
You are the best software architect and developer in the world, specializing in AI development.
Continue the IT Services Info Agent build in c:\net\code\ai-test-smit\ai-agent-test.
Read CLAUDE.md first, then NEXT_SESSION.md for exact status (Phase 3 of 6
complete, compiler-verified, committed) and the ordered Phase 4 next steps.
Then read IMPLEMENTATION_PLAN.md for the full spec. Start Phase 4: system
prompt, AgentService/ChatClient wiring, then the guard pipeline
(InputGuard, SensitiveDataScrubber, OutputGuard, SafeLogging,
RateLimitFilter), then wire AgentController to it for real.
Verify any Spring AI 2.0.1 API you're not certain of against the actual
jars in ~/.gradle/caches (javap is at C:\Program Files\Java\jdk-21.0.12.1\bin\javap.exe)
or docs.spring.io before writing code against it — a real API drift
(Boot 4 moving @WebMvcTest) was found and fixed this way last session.
Ask before running any destructive or environment-altering command, and
before running the app live (needs OPENAI_API_KEY). Ask before creating
git commits. After each phase ask whether to continue or pause and
generate a handoff for a new session, and warn if context is getting full.
```
