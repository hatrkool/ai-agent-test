# Handoff — continue IT Services Info Agent build

This file is the resume point for a fresh Claude Code session. Read
`IMPLEMENTATION_PLAN.md` and `ai-developer-task.md` first — this file only
tracks *progress against that plan*, not the plan itself.

## Status: Phase 2 in progress (of 6). Nothing compiled/run yet.

### Done

**Phase 1 — skeleton (complete, not yet build-verified):**
- `settings.gradle.kts`, `build.gradle.kts` — Boot `4.1.1`, Spring AI BOM
  `2.0.1`, `spring-ai-starter-model-openai`, `spring-ai-vector-store`,
  `integrationTest` source set wired.
- DTOs per plan §3: `AskRequest`, `SourceRef`, `Confidence` (lowercase via
  `@JsonValue`), `RefusalReason` (includes `SENSITIVE_DATA_IN_INPUT`),
  `AgentResponse`, plus `ApiError` for the exception handler.
- `AgentController` (hard-coded `refused=true/NOT_IMPLEMENTED` placeholder —
  **must be replaced in Phase 4** with a real call into `AgentService`),
  `HealthController`, `ApiExceptionHandler`.
- `application.yml`, `.env.example`, `.gitignore`.
- Unit tests: `AgentControllerApiTest` (API-01, API-02, SEC-07),
  `HealthControllerTest` (API-03). Both use `@WebMvcTest` slices, so no
  Spring AI/OpenAI bean is even loaded — passing proves "model never called"
  for free.

**Phase 2 — KB and retrieval (partially complete):**
- All 7 KB markdown files under `src/main/resources/kb/` — content is
  fictional, written carefully to avoid unintended lexical overlap between
  documents (see gotcha below).
- `kb/EstonianTextNormalizer` — fold diacritics, tokenize, 5-char stem;
  plus `foldAndLowercase()` (no stemming) for literal alias-phrase matching.
- `kb/KnowledgeChunk`, `kb/KnowledgeDocument`, `kb/KnowledgeBaseLoader`
  (parses front-matter + `##`-splits body; **also enforces the KB-side
  sensitive-data startup check** from plan §8 using the new
  `guard/SensitiveDataPatterns` class — fails startup naming file+detector,
  never the value).
- `kb/KnowledgeBaseIndex` — hybrid lexical+semantic scoring exactly per
  plan §5 (0.4 lexical / 0.6 semantic when semantic available; pure lexical
  otherwise), backed by `SimpleVectorStore` + OpenAI `EmbeddingModel`,
  degrades to lexical-only with no API key (verified via
  `ObjectProvider<EmbeddingModel>` + checking `spring.ai.openai.api-key` is
  non-blank before touching it).
- `guard/SensitiveDataPatterns` — the 5 detectors from plan §8 table
  (isikukood incl. mod-11 checksum, provider keys, JWT/bearer, password
  disclosure, IBAN). Returns only detector labels/positions, never matched
  values, for safe logging. **This will be reused, not duplicated, by the
  Phase 4 `SensitiveDataScrubber`.**
- Unit test `kb/KnowledgeBaseIndexTest` — drives UC-01..05 through the index
  in lexical-only mode (`semanticSearchEnabled=false`), asserting expected
  top-hit file per plan's Phase 2 "Done when".

### Not started yet
Phase 3 (tools + `RetrievalLedger`), Phase 4 (agent orchestration,
`system-prompt.txt`, `InputGuard`/`InjectionPatterns`,
`SensitiveDataScrubber` request-time half, `OutputGuard`, `SafeLogging`,
`RateLimitFilter`, `AgentProperties`), Phase 5 (integration tests, prompt
tuning), Phase 6 (CI workflow, README, SUMMARY.md).

The `AgentController` still returns a hard-coded refusal — nothing calls
`ChatClient` anywhere yet.

## Important: nothing has been compiled

Java/Gradle were not available at the start of this session; Java was
installed mid-session but the Gradle wrapper was never finished, and the
user asked to stop and hand off before running any build. **All Spring AI
2.0 API usage below was verified against live docs (see "APIs verified"),
not against a compiler.** Compile the project and fix any drift before
trusting it further — do this before writing more code on top of it.

A `.gradle/9.2.0/` cache directory exists in the repo root (gitignored) —
this implies Gradle 9.2.0 became resolvable at some point this session
(possibly via IDE background indexing), which may make wrapper setup
easier than starting from nothing. Check `gradle -v` / `java -version`
first before re-downloading anything.

### To finish the Gradle wrapper
No `gradle-wrapper.jar` / `gradlew` / `gradlew.bat` exist yet. Options tried
this session: no system `gradle`; downloaded `gradle-9.7.1-bin.zip` via
PowerShell `Invoke-WebRequest` to `$env:TEMP` and extracted it, but
`gradle.bat wrapper --gradle-version 9.7.1` failed on `JAVA_HOME not set`
before Java was installed. **Next step:** retry
`& "$env:TEMP\gradle-extract\gradle-9.7.1\bin\gradle.bat" wrapper --gradle-version 9.7.1`
from the repo root now that Java is installed (may need a fresh shell for
PATH/JAVA_HOME to pick up the new Java install). Then run `./gradlew test`
and fix whatever the compiler finds.

## APIs verified this session (safe to trust, cited from live docs 2026-09-09)

- Spring AI **2.0.1** is real, GA'd 2026-06-12, latest patch 2026-08-21.
  Starters pull **Spring Boot 4.1.0/4.1.1**, not just generic "4.x".
- Tool calling lives in a `ToolCallingAdvisor` in the `ChatClient` advisor
  chain (not in `ChatModel`), auto-registered by `ChatClient` — plan §1 is
  correct.
- Config keys are flat: `spring.ai.openai.chat.model`,
  `spring.ai.openai.chat.temperature` — **no** `.options` segment in 2.0.1.
  (An earlier search hit was 1.1.5 docs; the actual 2.0.1 adoc was fetched
  and confirms flat keys.)
- Jackson 3: only `jackson-annotations` (`com.fasterxml.jackson.annotation.*`,
  e.g. `@JsonValue`, `@JsonProperty`) keeps its old package. Everything else
  (`@JsonSerialize` etc.) moves to `tools.jackson.databind.annotation`. DTOs
  in this repo only use `@JsonValue`, so no problem so far — **but check
  this again if Phase 4's structured-output converter needs more Jackson
  annotations.**
- `org.springframework.ai:spring-ai-vector-store` is the module artifact for
  `SimpleVectorStore` (managed by the BOM, already added to
  `build.gradle.kts`). `SimpleVectorStore.builder(embeddingModel).build()`,
  `.add(List<Document>)`, `Document.builder().id().text().metadata(k,v).build()`,
  `SearchRequest.builder().query().topK().similarityThreshold().build()`,
  `vectorStore.similaritySearch(request)` returning `List<Document>` with
  `.getScore()` — all confirmed from the actual GitHub source / javadoc, not
  guessed.
- `EmbeddingModel.embed(String)` returns `float[]` — not used directly yet
  (index goes through `VectorStore`/`Document` instead), but confirmed in
  case Phase 4 needs it.

## Gotcha already hit and fixed — read before touching KnowledgeBaseIndex

First alias-boost design used **stemmed single-token overlap** between query
and each document's alias list. This caused false ties: e.g. for UC-01
("Kuidas taotleda ligipääsu GitLabile?"), `access-requests.md` matched
almost as strongly as `gitlab-access.md` because its alias
`ligipääsutaotlus` stems to the same 5-char token `ligip` as the query, and
its body incidentally said "(nt GitLab, VPN)" — pulling in the `gitla` token
too. Fixed two ways:
1. `KnowledgeBaseIndex` alias matching now uses **literal folded-phrase
   substring containment** (`foldedQuery.contains(foldedAlias)`) instead of
   stemmed token-set overlap — much more precise, no more single-stem
   collisions.
2. Removed the incidental "(nt GitLab, VPN)" example from
   `access-requests.md` — a general/overview KB doc should not name a
   system that has its own dedicated doc, or lexical bleed-through recurs.

**When writing more KB docs or tuning scoring in Phase 5, keep re-deriving
this by hand is not sustainable — get the build running and let the unit
test (`KnowledgeBaseIndexTest`) catch collisions instead of manual tracing.**

## Environment gotchas for this machine (Windows, PowerShell primary)

- No `curl`/native download tool reliably available to the user — use
  `PowerShell`'s `Invoke-WebRequest`, not `Bash`'s `curl` (Bash tool is Git
  Bash and does have a `curl.exe` under mingw64, but the user said they
  don't have it — stick to PowerShell for downloads).
- Don't run Gradle/Java version-check commands speculatively — the user
  wants to be asked before any tool-install/environment-probing action, and
  before any actual build/run is attempted. Ask first.

## Next steps in order

1. Get `./gradlew test` running green for what exists (Phase 1 + Phase 2
   unit tests). Fix any compile errors the Spring AI API guesses above
   turn out to have.
2. Phase 3: `tools/KnowledgeBaseTools` (3 `@Tool` methods —
   `listTopics`, `searchKnowledgeBase`, `getDocument`), `tools/ToolConfig`
   registering them as `ToolCallback` beans, `tools/RetrievalLedger`
   (`@RequestScope`). `getDocument` must do a map lookup only, never touch
   the filesystem (SEC-06 by construction).
3. Phase 4: system prompt file, `AgentService`/`AgentPromptFactory` wiring
   `ChatClient` + tools + chat memory, `InputGuard`/`InjectionPatterns`,
   `SensitiveDataScrubber` (request-time half — reuse
   `guard/SensitiveDataPatterns`), `OutputGuard` (5 checks from plan §8),
   `SafeLogging`, `RateLimitFilter` (add `bucket4j-core` dependency then),
   `config/AgentProperties`. Replace the placeholder `AgentController` body
   with a real call into `AgentService`.
4. Phase 5: integration test source set content for all UC/SEC/API-04
   scenarios (needs `OPENAI_API_KEY` from the user to actually run).
5. Phase 6: CI workflow, README, SUMMARY.md.

## Prompt to paste into a new session

```
You are the best software architect and developer in the world, specializing in AI development.
Continue the IT Services Info Agent build in c:\net\code\ai-test-smit\ai-agent-test.
Read NEXT_SESSION.md first — it has exact status, what's verified, a gotcha
already fixed, and the next steps in order. Then read IMPLEMENTATION_PLAN.md
for the full spec. Start by getting ./gradlew test running (see "To finish
the Gradle wrapper" in NEXT_SESSION.md), fix whatever the compiler finds in
the existing Phase 1/2 code, then continue with Phase 3 (tools + ledger).
Ask before running any destructive or environment-altering command. After
every phase ask if user wants to continue to next phase or generate memory and input 
for next chat to continue in new context. Also warn if context window is getting full.
```
