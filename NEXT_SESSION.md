# Handoff — IT Services Info Agent build

This file is the resume point for a fresh Claude Code session. Read
`CLAUDE.md` first (project-specific rules and invariants), then
`IMPLEMENTATION_PLAN.md` for the full spec. This file only tracks
*progress against that plan*, not the plan itself.

## Status: all 6 phases complete and committed.

The plan's 6-phase build (`IMPLEMENTATION_PLAN.md` §12) is done. Phase 5
(integration tests, live-verified 3x) and Phase 6 (CI workflow, README,
SUMMARY) both landed this and the previous session.

### Commit history

```
663b4b7 docs: README and one-page summary                              (Phase 6)
da4bd64 ci: add GitHub Actions workflow for unit and integration tests (Phase 6)
4da6c23 docs: update handoff notes after Phase 5
b4921c4 test: integration coverage for all UC and SEC scenarios       (Phase 5)
551d203 fix: accept case-insensitive Confidence values in structured output
b4bab71 feat: agent orchestration with pre- and post-model guards      (Phase 4)
af4b4ba docs: update handoff notes after Phase 3
6744c6c feat: knowledge base tools with allowlist and retrieval ledger (Phase 3)
44b9553 build: Gradle wrapper, Checkstyle, and a CLAUDE.md project guide
822c257 feat: knowledge base loading and hybrid retrieval              (Phase 2)
2582a26 feat: project skeleton, API contract and input validation      (Phase 1)
```

Note: Phase 6 was split into two commits rather than the plan's suggested
single `docs: README, summary and CI workflow` — the user asked for the
CI workflow separated from the documentation. Functionally equivalent;
just a different split of the same content.

### Phase 6 — CI, documentation (complete)

- `.github/workflows/ci.yml` — matches plan §11 exactly: `unit-tests` job
  always runs; `integration-tests` job needs `push` + a present
  `OPENAI_API_KEY` secret (guarded via `steps.guard.outputs.has_key`);
  both upload their HTML report as a separate artifact.
- `README.md` — full rewrite from the placeholder stub. Covers: purpose
  and scope; Java 21/Boot 4.1.1/Spring AI 2.0.1/Gradle 9.7.1 versions;
  local run instructions and env vars; `curl` examples for both a
  grounded answer and a refusal; an architecture diagram and package
  list; the full security model (all 5 guard layers, with the documented
  policy choices for injection refusal, SEC-04, and the sensitive-data
  redact-vs-refuse split); the SEC-06 path-traversal-by-construction
  argument; the `refused==false` invariant and its two documented
  exceptions (UC-05, UC-07); a data-handling paragraph; the full
  ID→test mapping table; HTML report paths; known limitations (plan §13
  verbatim as a base, plus the UC-13 tool-recall gap from Phase 5,
  written out in full); and a "Deviations from the implementation plan"
  section recording the three live-testing surprises from Phase 5 (the
  `spring-boot-resttestclient` artifact, the `Confidence` `@JsonCreator`
  fix, and the narrower-than-expected Jackson 3 annotation-package split).
- `SUMMARY.md` — one page: architecture, the 6 security mechanisms with
  justification, known limitations (condensed from README).
- Content was cross-checked against the actual source (`build.gradle.kts`,
  DTOs, guard classes, `KnowledgeBaseTools`, test method names) rather
  than assuming the plan's draft matched the implementation verbatim —
  it mostly did, with the three deviations noted above.

**Caveat, read before trusting this as fully done:** the plan's Phase 6
"done when" bar is "a clean clone, `./gradlew test`, and the documented
run command work with nothing but the README as guidance." The user
explicitly chose to skip running `./gradlew test` / `checkstyleMain` this
session (documentation-only session, per CLAUDE.md's "ask before the
first build/test of a session" rule — they said skip). So the README's
accuracy rests on cross-referencing source files, not on an actual build.
**A fresh session should run `./gradlew test` and `./gradlew
checkstyleMain` once (ask first, per CLAUDE.md) before treating Phase 6
as fully verified**, especially before submission.

### Not yet done (post-plan, submission-related)

The plan's own 6 phases are complete, but the actual take-home submission
("Tarnitavad tulemused": repo link, README, one-page summary, link to a
CI run with the HTML report artifact) needs a few things not yet
discussed with the user:

1. **No GitHub remote is configured yet** — `git log` shows 10 local
   commits ahead of nothing (no `origin` push has happened). A CI run
   link is part of the deliverables, which needs an actual push to a
   GitHub remote with Actions enabled and `OPENAI_API_KEY` set as a repo
   secret for the integration job to run in CI.
2. Once pushed, confirm the Actions run is green (or at least that the
   unit-tests job is — the integration job depends on the secret being
   configured) and grab the run URL for the submission.
3. Decide how to hand over the "repo link" and "one-page summary" per the
   brief's delivery format — not yet discussed.

None of this was addressed this session — the user asked to leave it for
later. A fresh session (or a later turn in this one) should raise it
explicitly before calling the exercise submitted, not assume it's done
because the code and docs are.

## Environment / workflow notes carried forward

(Unchanged from the Phase 5 handoff — still accurate as of this update.)

- `.env` at the repo root has a real `OPENAI_API_KEY` (gitignored, never
  committed). Load it into a shell before any live command:
  `set -a && source .env && set +a` (bash).
- Manual `bootRun` debugging pattern: start in background
  (`./gradlew bootRun --console=plain > logfile 2>&1 &`), poll
  `/api/v1/health` until 200, find the PID via `wmic process where
  "name='java.exe'" get ProcessId,CommandLine /format:list` (grep for
  `ItAgentApplication`), `taskkill //PID <pid> //F` to stop. Watch for
  stale instances left over from a prior turn still holding port 8080.
- Windows/Git Bash `curl` mangles non-ASCII (Estonian ä/õ/ü/ö) inline
  arguments — write the JSON body to a UTF-8 file and use
  `curl --data-binary @file.json`, never `-d "..."` with Estonian text.
- `./gradlew integrationTest` is cached like any other `Test` task — use
  `--rerun` for repeat runs with no source changes, or it silently
  reports `UP-TO-DATE` without re-hitting the live model.

See the git history (Phase 4/5 commits) for the full list of Spring AI
2.0/Boot 4 API gotchas found during earlier phases — not repeated here to
keep this file from growing without bound; README.md's "Deviations from
the implementation plan" section is now the canonical write-up of the
ones worth a permanent record.

## Prompt to paste into a new session

```
Continue the IT Services Info Agent build in c:\net\code\ai-test-smit\ai-agent-test.
Read CLAUDE.md first, then NEXT_SESSION.md for exact status: all 6 plan
phases are complete and committed, but (a) the Phase 6 "done when" bar
(a clean-clone ./gradlew test run) was never re-verified this session —
ask before running it, per CLAUDE.md — and (b) the actual submission
still needs a GitHub remote, a push, and a CI run link, none of which
has been set up or discussed yet. Read IMPLEMENTATION_PLAN.md for the
full spec if needed. Start by asking the user what they want to do next:
verify the build, work on the remote/CI/submission steps, or something
else — don't assume either is wanted without asking.
```
