# Summary — IT Services Info Agent

Spring Boot 4.1.1 / Spring AI 2.0.1 (Java 21) service answering Estonian
IT-helpdesk questions grounded strictly in a static, seven-document Markdown
knowledge base, with source citations and code-enforced refusal instead of
hallucination. Full detail in `README.md` and `IMPLEMENTATION_PLAN.md`.

## Architecture

```text
Bean Validation → RateLimitFilter → InputGuard (pre-LLM) →
SensitiveDataScrubber → ChatClient + ToolCallingAdvisor → OutputGuard (post-LLM) → response
```

- **Retrieval:** hybrid lexical (normalised-token overlap, alias-boosted) +
  semantic (`SimpleVectorStore`, cosine similarity), `0.4/0.6` weighted,
  top-4 above a 0.35 threshold. Degrades to lexical-only with no API key.
- **Tools (exactly three, allowlist-tested):** `listTopics`,
  `searchKnowledgeBase`, `getDocument` — no filesystem/network/shell access
  from any tool.
- **Retrieval ledger:** a `@RequestScope` bean recording every chunk any
  tool actually returned this request — the sole source of truth
  `OutputGuard` checks citations against.

## Security mechanisms, and why

1. **Deterministic pre-model refusal (`InputGuard`).** Regex-based, both
   languages, refuses before any model call. Chosen over "warn the model
   and let it decide" because SEC-01/03/04/08 must hold on every run, not
   just when the model happens to comply — a hidden-instruction question
   (SEC-04) is refused wholesale rather than partially answered, for the
   same reason: simpler and fully deterministic, at the cost of not
   answering the legitimate part of a mixed question.
2. **Split sensitive-data policy (`SensitiveDataScrubber`).** Identifiers
   (isikukood, IBAN) are redacted and the request continues, since they
   can appear incidentally in a legitimate question; credentials
   (provider keys, JWT/bearer tokens, disclosed passwords) refuse
   outright, since a leaked secret is treated as an incident, not
   forwarded. Runs a second time at KB-loading startup — a document
   containing a sensitive-data-shaped string fails the boot, naming the
   file and detector but never the value.
3. **Code-verified citations, never model-trusted (`OutputGuard`).** Every
   `SourceRef` and excerpt in the model's structured output is checked
   against the `RetrievalLedger` after the model runs; an unverifiable
   source is downgraded to a `NO_SOURCE_MATCH` refusal. This is the entire
   mechanism behind "does not hallucinate" — the model's own claim about
   where an answer came from is never authoritative.
4. **Path traversal prevented by construction (SEC-06).** `getDocument` is
   a startup-built `Map<String, KnowledgeDocument>` lookup; there is no
   `Path`/`File` construction from user input anywhere on the request
   path, so sanitisation isn't needed because there is no filesystem
   access to sanitise into.
5. **Leak-resistant output.** `OutputGuard`'s fifth check compares the
   answer's 6-grams against the system prompt (>15% overlap refuses) and
   rejects any of the three tool names appearing verbatim — catching
   prompt/tool exfiltration even from an otherwise well-formed, refused or
   non-refused answer.
6. **Safe logging.** Never logs question text at INFO+; only a SHA-256
   prefix, length, session id, and matched pattern/detector *labels*.

Validated against a live model: 21/21 integration scenarios (UC-01..13,
SEC-01..08 excl. SEC-07 which is unit-only, API-04), three consecutive
green full runs.

## Known limitations

- Injection defence is heuristic (regex); `OutputGuard` is the real
  backstop and constrains damage rather than preventing every bypass.
- Retrieval is bounded by seven documents and a crude 5-character stemmer,
  not a real Estonian morphological analyser.
- Sensitive-data detection is pattern-based; it does not catch secrets
  with no recognisable shape and is not a DLP system.
- Session memory and rate limiting are in-process/in-memory — no
  multi-instance support.
- No authentication on the endpoint (explicitly out of scope here).
- **UC-13** (a pure provenance follow-up, "Kust see info pärineb?") shows
  materially lower tool-recall reliability than every other tested
  follow-up phrasing, despite four rounds of prompt-engineering
  (mandatory-reinvocation rule, worked example, rationale, tool-description
  nudge). The model sometimes treats a citation already visible in its own
  prior message as sufficient, not understanding that the verification
  ledger resets every request. Not a code defect: the architecture's
  actual, always-held guarantee — and what the test asserts — is grounded
  citation if the model re-verifies, clean refusal with no fabricated
  source if it doesn't.
