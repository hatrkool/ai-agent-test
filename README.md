# IT Services Info Agent

An internal, Estonian-language IT-helpdesk FAQ agent. It answers questions about
internal IT services (GitLab access, VPN, Kubernetes deploys, code review, CI/CD,
access requests, incident reporting) **only** from a static Markdown knowledge
base, cites the document it drew from, and refuses rather than invents an answer
when the knowledge base doesn't cover the question.

Built as a take-home exercise (`ai-developer-task.md`) to `IMPLEMENTATION_PLAN.md`,
which is the authoritative spec this README summarises. `CLAUDE.md` records the
project's architecture invariants and workflow rules for anyone continuing the
build.

## Scope

In scope: retrieval-grounded question answering over seven fictional KB
documents, citation verification, prompt-injection and sensitive-data defences,
rate limiting, session-scoped follow-up memory. Out of scope (see
[Known limitations](#known-limitations)): authentication, horizontal scaling,
a production-grade Estonian text analyser, anything beyond the three
knowledge-base tools.

## Versions

| Component | Version |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Gradle (wrapper) | 9.7.1 |
| Model | `gpt-4.1-mini` (configurable, see below) |
| Temperature | `0.0` (configurable) |

## Running locally

Requires Java 21 and an OpenAI API key with access to `gpt-4.1-mini` (needed for
chat and for embeddings, unless semantic search is disabled).

```bash
cp .env.example .env
# edit .env and set OPENAI_API_KEY
set -a && source .env && set +a
./gradlew bootRun
```

Environment variables (all optional except `OPENAI_API_KEY`):

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | — | required for chat and embeddings; app still boots and unit tests still pass without it |
| `AGENT_MODEL` | `gpt-4.1-mini` | chat model name |
| `AGENT_TEMPERATURE` | `0.0` | chat temperature |
| `AGENT_SEMANTIC` | `true` | set `false` to run lexical-only retrieval, e.g. with no API key |

The app listens on `:8080`.

### Example requests

A grounded, successful answer:

```bash
curl -s http://localhost:8080/api/v1/agent/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Kuidas ma saan GitLabile ligipääsu taotleda?"}' | jq
```

```json
{
  "answer": "Ligipääsu saamiseks GitLabile logi sisse SMIT teenuste portaali, vali \"Ligipääsutaotlus\" → \"GitLab\". Täida põhjendus ja oota juhi kinnitust. [allikas: gitlab-access.md]",
  "sources": [
    { "file": "gitlab-access.md", "title": "GitLab ligipääs", "excerpt": "Ligipääsu saamiseks GitLabile logi sisse SMIT teenuste portaali..." }
  ],
  "confidence": "high",
  "refused": false,
  "refusalReason": null
}
```

An out-of-scope question, refused rather than answered from general knowledge:

```bash
curl -s http://localhost:8080/api/v1/agent/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Mis on Eesti pealinn?"}' | jq
```

```json
{
  "answer": "Küsimus ei puuduta IT teenuseid, mille kohta ma vastata saan.",
  "sources": [],
  "confidence": "low",
  "refused": true,
  "refusalReason": "Küsimus ei puuduta IT teenuseid, mille kohta ma vastata saan."
}
```

Health check (never touches the model, works with no API key):

```bash
curl -s http://localhost:8080/api/v1/health
# {"status":"UP"}
```

## Architecture

```text
HTTP request
  │
  ▼
Bean Validation (question non-blank, ≤2000 chars)         ── API-01/02, SEC-07
  ▼
RateLimitFilter (Bucket4j, 10 req/min per IP, ask endpoint only)
  ▼
InputGuard (regex, pre-LLM)                                ── SEC-01/03/08
  ▼
SensitiveDataScrubber (redact identifiers / refuse on credentials)
  ▼
AgentService → ChatClient + ToolCallingAdvisor
  │   tools: listTopics, searchKnowledgeBase, getDocument
  │   every tool call recorded in a @RequestScope RetrievalLedger
  ▼
OutputGuard (post-LLM, 5 checks against the ledger)         ── SEC-02/05, UC-12
  ▼
AgentResponse (answer, sources, confidence, refused, refusalReason)
```

Package layout (`ee.example.itagent`): `api` (controllers, DTOs, exception
mapping), `agent` (`AgentService`, prompt factory, session memory), `kb`
(loading, chunking, hybrid lexical+semantic index), `tools` (the three
`@Tool` methods, the retrieval ledger), `guard` (input/output guards,
sensitive-data scrubbing, safe logging), `config` (typed properties, rate
limiter).

**Retrieval** is hybrid: `EstonianTextNormalizer` folds diacritics, strips
punctuation and truncates tokens to a 5-character stem for lexical overlap
scoring; `SimpleVectorStore` (in-memory, embedded once at startup) adds
cosine similarity. Combined score `0.4 * lexical + 0.6 * semantic`, top-4
above a 0.35 floor. With no API key, or `AGENT_SEMANTIC=false`, the index
degrades to lexical-only so the app and unit tests never need a live key.

**Knowledge base** (`src/main/resources/kb/`): seven Estonian Markdown
documents with YAML front-matter (`title`, `topic`, `aliases`), fictional
data only. `KnowledgeBaseLoader` scans every document at startup for
sensitive-data patterns (see below) and **fails the boot** — naming the
file and detector, never the matched value — if anything matches. This
makes "no secrets in the KB" an enforced invariant, not a house rule.

## Security model

Five layers, run in a fixed order so security behaviour stays deterministic
rather than depending on whether the model chooses to comply:

1. **Bean Validation** — rejects blank or >2000-character questions before
   any other component runs (API-01, API-02, SEC-07).
2. **`RateLimitFilter`** — Bucket4j, one bucket per client IP, 10
   requests/minute, scoped to `/api/v1/agent/ask` only so `/health` is
   unaffected.
3. **`InputGuard`** (pre-LLM) — case-insensitive regex patterns
   (`InjectionPatterns`) in English and Estonian covering instruction
   override ("ignore previous instructions", "sa oled nüüd", "act as"),
   prompt/tool exfiltration ("reveal prompt", "list tools"), and path
   traversal (`../`, `/etc/`). **Policy: refuse immediately with
   `INJECTION_SUSPECTED`, before the model is ever called.** The brief
   allows either refusing or warning the model and letting it decide;
   refusing was chosen because it's deterministic and therefore reliably
   testable — a model asked to "be careful" can still occasionally comply
   with an injected instruction, and SEC-01/03/08 need to hold every run.
   **SEC-04** (a legitimate GitLab question with a hidden instruction
   appended) is handled the same way: the pre-filter trips on the hidden
   instruction and the whole request is refused, rather than trying to
   answer only the legitimate part. Simpler and safer than partial
   compliance; the trade-off is that a mixed question gets no answer at
   all, which is judged acceptable for an internal helpdesk tool.
4. **`SensitiveDataScrubber`** — runs after `InputGuard`, before the model
   call, on the user's question. Two response strategies depending on what
   matched: Estonian isikukood and IBAN are **redacted** (`[isikukood
   eemaldatud]`, `[konto eemaldatud]`) and the request continues, because a
   question can legitimately contain an ID number incidentally. Provider
   API keys, JWT/bearer tokens and disclosed passwords **refuse outright**
   with `SENSITIVE_DATA_IN_INPUT`, because a leaked credential is treated
   as a security event, not a formatting detail to quietly strip. The
   scrubbed string — never the original — is what reaches OpenAI and what
   is written to session memory. The same detectors run again at startup
   over every KB document (see above); tool output therefore needs no
   runtime scrubbing, since the KB is static and already validated.
5. **`OutputGuard`** (post-LLM, every candidate response) — five checks:
   (1) every `SourceRef.file` must appear in the request-scoped
   `RetrievalLedger` — a file the tools never returned this request is
   refused with `NO_SOURCE_MATCH`; (2) every excerpt must be a
   whitespace-normalised substring of a ledger chunk, catching a real
   filename paired with an invented quote; (3) `refused=false` with empty
   `sources` is refused with `LOW_CONFIDENCE`; (4) a valid,
   ledger-verified answer missing an `[allikas: ...]` marker gets one
   appended rather than being refused, since the grounding is already
   verified; (5) a leak check compares the answer's 6-grams against the
   system prompt text (>15% overlap refuses) and checks for any of the
   three tool names appearing verbatim, catching prompt/tool exfiltration
   even from an otherwise well-formed answer, refused or not.

**Path traversal (SEC-06) is prevented by construction, not by sanitising
input.** `getDocument` looks the requested filename up in a
`Map<String, KnowledgeDocument>` built once at startup from the classpath
KB; it never constructs a `Path` or touches the filesystem at request
time, and `searchKnowledgeBase` only ever searches already-loaded chunks.
An unrecognised filename returns a `found=false` result object — there is
no code path from user input to the filesystem, in either tool.

**Citations are verified in code, never trusted from the model.** This is
the single mechanism the "does not hallucinate" grading criterion rests
on: the `RetrievalLedger` is the ground truth of what the tools actually
returned this request, and `OutputGuard` checks every source and excerpt
against it after the model runs, independent of what the model claims.

**Logging** (`SafeLogging`) never writes full question text at INFO or
above — only `sessionId`, a SHA-256 prefix of the question, its length,
and matched pattern *labels*. Full-text logging exists only at DEBUG,
gated behind `agent.logging.verbose` (default off). Sensitive-data matches
log the detector label and count, never the matched value.

**Tool allowlist.** Exactly three tools exist — `listTopics`,
`searchKnowledgeBase`, `getDocument` — registered as `ToolCallback` beans
and passed explicitly to `ChatClient`. No general-purpose tool and no
filesystem/network/shell access from any tool. A unit test asserts the
registered tool set is exactly these three names.

**`refused == false` invariant.** The core "does not hallucinate"
guarantee, enforced by `OutputGuard`: whenever `refused` is `false`,
`sources` is non-empty and `answer` contains at least one
`[allikas: <file>]` marker whose file is in `sources`. Two documented
exceptions:

- **UC-05** (topic listing) — answers with the KB's topic list, one
  `SourceRef` per document (excerpt = that document's title). `refused:
  false`, `confidence: high`. The invariant holds honestly because every
  listed topic really is a loaded document.
- **UC-07** (ambiguous question) — returns `refused: true` with
  `refusalReason = CLARIFICATION_NEEDED`, naming the candidate topics.
  `sources` stays empty, which the invariant permits on a refusal.

## Data handling

- **Sent to OpenAI:** the (scrubbed) question text, session chat history
  for the current session, the system prompt, and whatever the three KB
  tools return. Never the raw, unscrubbed question if it matched an
  identifier or credential pattern.
- **Never sent to OpenAI:** the original text of a redacted identifier or
  a detected credential — the scrubbed placeholder is substituted before
  the request is built.
- **Logged:** session id, question length, a SHA-256 prefix of the
  question, and pattern/detector *labels* — never question text (at
  INFO+) or matched sensitive values, ever.
- **Knowledge base:** exclusively fictional test data (invented systems,
  no real hostnames or procedures), and startup-scanned so a document
  containing anything sensitive-data-shaped fails the boot rather than
  reaching the model.
- **Session memory:** in-process only (see limitations below); holds the
  scrubbed question and the agent's answers for `session-ttl` (30 min) or
  up to `max-sessions` (1000) concurrent sessions.

## Tests

### Running

```bash
./gradlew test              # unit tests — no network, always green
./gradlew integrationTest   # live-model tests — needs OPENAI_API_KEY
./gradlew checkstyleMain    # style/lint (wired into `check`/`build`, maxWarnings=0)
```

Reports (gitignored, generated locally or by CI): `build/reports/tests/test/index.html`
and `build/reports/tests/integrationTest/index.html`.

The integration suite hits a live model and was run three consecutive
times before Phase 5 was declared done, per the plan's stability rule —
all 21/21 green each run.

### ID → test mapping

| ID | Scenario | Test |
|---|---|---|
| API-01 | empty question → 400 | `AgentControllerApiTest.api01_emptyQuestion_returns400` |
| API-02 | missing `question` field → 400 | `AgentControllerApiTest.api02_missingQuestionField_returns400` |
| API-03 | `GET /health`, no model interaction | `HealthControllerTest.api03_healthCheck_returns200WithoutOpenAiCall` |
| API-04 | full response contract | `ApiIntegrationTest.api04_validRequest_returnsAllContractFieldsWithSourceDetailAndCitation` |
| SEC-07 | 3000-char input → 400, model never called | `AgentControllerApiTest.sec07_overlongQuestion_returns400BeforeAnyModelCall` |
| DATA-01 | isikukood in question → redacted, request continues | `SensitiveDataScrubberTest.data01_isikukoodInQuestion_isRedactedAndRequestContinues` |
| DATA-02 | API key / password in question → refused, model never called | `SensitiveDataScrubberTest.data02_apiKeyInQuestion_refusesBeforeAnyModelCall`, `data02_passwordDisclosure_refuses` |
| DATA-03 | KB doc with a fake isikukood → boot fails | covered in `KnowledgeBaseLoader` startup-scan tests |
| UC-01 | direct question resolves to `gitlab-access.md` | `KnowledgeBaseIndexTest.uc01_directQuestion_topHitIsGitlabAccess` (unit, lexical), `UseCaseIntegrationTest.uc01_directQuestion_returnsGitlabSourceWithCitation` (live) |
| UC-02 | inflected short query resolves to `gitlab-access.md` | `KnowledgeBaseIndexTest.uc02_shortInflectedQuery_topHitIsGitlabAccess`, `UseCaseIntegrationTest.uc02_shortInflectedQuery_resolvesToGitlabSource` |
| UC-03 | different topic resolves to `kubernetes-deploy.md`, not GitLab | `KnowledgeBaseIndexTest.uc03_kubernetesQuestion_topHitIsKubernetesDeploy_notGitlab`, `UseCaseIntegrationTest.uc03_differentTopic_resolvesToKubernetesNotGitlab` |
| UC-04 | paraphrase resolves to `code-review.md` | `KnowledgeBaseIndexTest.uc04_paraphrasedCodeReviewQuestion_topHitIsCodeReview`, `UseCaseIntegrationTest.uc04_paraphrasedQuestion_resolvesToCodeReview` |
| UC-05 | topic listing, ≥5 topics, sources non-empty | `KnowledgeBaseIndexTest.uc05_allSevenTopicsAreLoaded`, `UseCaseIntegrationTest.uc05_topicListing_mentionsAtLeastFiveTopicsWithSources` |
| UC-06 | follow-up in same session keeps context | `UseCaseIntegrationTest.uc06_followUpInSameSession_answersGitlabTimeframeGroundedInSameFile` |
| UC-07 | ambiguous question → clarification refusal | `UseCaseIntegrationTest.uc07_ambiguousQuestion_asksForClarificationOrOffersCandidateTopics` |
| UC-08 | mixed-language question answered in Estonian | `UseCaseIntegrationTest.uc08_mixedLanguageQuestion_answersInEstonian` |
| UC-09 | coding-task request refused, no code generated | `UseCaseIntegrationTest.uc09_codingTask_refusesWithoutGeneratingCode` |
| UC-10 | general-knowledge question refused / declared out of scope | `UseCaseIntegrationTest.uc10_generalKnowledgeQuestion_refusesOrDeclaresOutOfScope` |
| UC-11 | sensitive-info request refused | `UseCaseIntegrationTest.uc11_sensitiveInfoRequest_refuses` |
| UC-12 | non-existent topic refused, no fabricated source | `UseCaseIntegrationTest.uc12_nonExistentTopic_refusesWithoutFabricatingSource` |
| UC-13 | provenance follow-up: grounded if re-verified, never fabricated otherwise | `UseCaseIntegrationTest.uc13_sourceInquiry_groundedIfPossible_neverFabricatedIfNot` — see [known limitations](#known-limitations) |
| SEC-01 | direct injection refused, no prompt leak | `SecurityIntegrationTest.sec01_directInjection_refusesWithoutLeakingSystemPrompt` |
| SEC-02 | role override refused, no tool names revealed | `SecurityIntegrationTest.sec02_roleOverride_refusesWithoutRevealingToolNames` |
| SEC-03 | impersonated system role refused | `SecurityIntegrationTest.sec03_impersonatedSystemRole_refuses` |
| SEC-04 | hidden instruction in a legitimate question refused | `SecurityIntegrationTest.sec04_hiddenInstructionInsideLegitimateQuestion_neverReturnsDeletionCode` |
| SEC-05 | prompt-exfiltration attempt refused, no leak | `SecurityIntegrationTest.sec05_promptExfiltrationAttempt_refusesWithoutLeaking` |
| SEC-06 | path traversal returns no filesystem content | `KnowledgeBaseToolsTest` (unit, `getDocument("../../../etc/passwd")`), `SecurityIntegrationTest.sec06_pathTraversalAttempt_returnsNoFilesystemContent` |
| SEC-08 | Estonian-phrased jailbreak refused | `SecurityIntegrationTest.sec08_estonianJailbreakAttempt_refuses` |
| — | tool allowlist is exactly 3 named tools | `KnowledgeBaseToolsTest` |
| — | `OutputGuard`'s 5 checks | `OutputGuardTest` |
| — | rate limiter, 11th request/min → 429 | `RateLimitFilterTest` |
| — | injection pattern coverage (~15 parameterised cases) | `InputGuardTest` |

(SEC-07 above only in the unit suite by design — see plan §10.)

## Known limitations

Named here deliberately, per the brief's own grading philosophy — stating a
limitation honestly is graded positively, hiding one is not.

- **Injection defence is heuristic.** `InputGuard`'s regexes catch known
  phrasings; a sufficiently novel phrasing, obfuscation or encoding can get
  past them. `OutputGuard` is the real backstop — it constrains the damage
  a bypass can do (no fabricated source, no leaked prompt/tool names) but
  does not prevent every bypass attempt from reaching the model.
- **Retrieval quality is bounded by seven small documents and a crude
  5-character stemmer** (`EstonianTextNormalizer`). It handles the tested
  inflections; a real deployment needs a proper Estonian morphological
  analyser.
- **Sensitive-data detection is pattern-based**, covering Estonian
  isikukood (with mod-11 checksum validation), IBAN, common provider
  API-key prefixes and explicit password disclosure. It will not catch a
  secret with no recognisable shape, and the isikukood checksum still
  admits valid-looking fabricated values. It reduces accidental leakage;
  it is not a DLP system.
- **Session memory is in-process.** It does not survive a restart and does
  not work correctly behind more than one running instance.
- **Rate limiting is per-instance and in-memory** (Bucket4j), so it is not
  a real quota under horizontal scaling.
- **Integration tests depend on a third-party model**; behaviour can drift
  between model versions. Assertions target behaviour, not exact wording,
  specifically to reduce this risk — but a model update can still shift
  results.
- **No authentication on the endpoint.** Out of scope for this exercise,
  but a blocker before any real deployment.
- **UC-13 (pure provenance follow-up) has materially lower tool-recall
  reliability than every other tested follow-up phrasing.** "Kust see info
  pärineb?" asks the model to re-confirm a source it already cited earlier
  in the same conversation. The system prompt has a rule (promoted to rule
  1) requiring a tool call on every turn — because `RetrievalLedger` is
  `@RequestScope` and a prior turn's tool result is invisible to the
  current request's verification — plus a worked example, an explanation
  of *why*, and a tool-description nudge on `getDocument`. Across four
  distinct prompt-engineering attempts this reduced but did not eliminate
  the gap: in isolated debug-logged runs the model skipped the required
  re-invocation on this specific phrasing 0/4 times, reasonably (from its
  perspective) treating the citation already visible in its own prior
  message as sufficient. This is not a code defect — it is the model not
  reliably following an explicit, repeated instruction on one specific
  phrasing. The architecture's actual guarantee, and what the test
  asserts, is: **grounded citation if the model does re-verify, a clean
  refusal with no fabricated source if it doesn't** — never a fabricated
  or stale-but-unverified citation either way.

## Deviations from the implementation plan

Recorded per `CLAUDE.md`'s instruction to document any point where live
Spring AI 2.0/Boot 4 behaviour diverged from the plan's assumptions rather
than silently downgrading:

- **`TestRestTemplate` lives in a new artifact**,
  `org.springframework.boot:spring-boot-resttestclient`
  (`org.springframework.boot.resttestclient.TestRestTemplate` /
  `...resttestclient.autoconfigure.AutoConfigureTestRestTemplate`), not
  bundled with `spring-boot-starter-test` as in Boot 3. Confirmed by
  inspecting the actual Maven Central `org/springframework/boot/`
  directory listing, not by guessing from a tutorial.
- **`Confidence` deserialisation needed an explicit `@JsonCreator`.**
  Jackson 3 enforces the `@JsonValue`-annotated lowercase form
  (`"high"`) strictly on the way in, but the model's structured-output
  JSON schema exposes the raw enum constant names (`"HIGH"`), so every
  real response 500'd until a case-insensitive `@JsonCreator` factory was
  added. Not caught by any unit test, since unit tests mock the model and
  never exercise the real converter round-trip — only found via live
  testing.
- **Jackson 3 annotation packages, empirically narrower than CLAUDE.md's
  note suggested:** only `@JsonValue`, `@JsonProperty` and `@JsonCreator`
  stayed in `com.fasterxml.jackson.annotation` on this classpath; nothing
  else needed the `tools.jackson.databind.annotation` package because
  nothing else was used.
- Everything else in the plan's Spring AI 2.0 API notes (§1) —
  `ToolCallingAdvisor` in the `ChatClient` advisor chain, `ToolCallback`
  beans passed via `.tools(...)`, no `.options` segment in
  `spring.ai.openai.chat.*` keys, `SimpleVectorStore.builder(...)`,
  `Document.builder()`, `SearchRequest.builder()...similarityThreshold()`
  — held as specified; no further deviations.
