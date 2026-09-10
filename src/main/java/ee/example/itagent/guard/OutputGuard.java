package ee.example.itagent.guard;

import ee.example.itagent.agent.AgentPromptFactory;
import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.Confidence;
import ee.example.itagent.api.dto.RefusalReason;
import ee.example.itagent.api.dto.SourceRef;
import ee.example.itagent.kb.KnowledgeChunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Post-model verification (plan §8, five checks). Citations are trusted only
 * once checked against what the tools actually returned this request — see
 * CLAUDE.md invariant 1; {@code sources}/excerpts the ledger never produced
 * are never let through. The leak check (5) runs on every candidate answer,
 * refused or not, since a refusal can still accidentally quote the system
 * prompt or a tool name.
 */
@Component
public class OutputGuard {

    private static final double LEAK_THRESHOLD = 0.15;
    private static final int GRAM_SIZE = 6;
    private static final Pattern CITATION_MARKER = Pattern.compile("\\[allikas:\\s*([^\\]]+)]");

    private final String systemPromptText;
    private final List<String> toolNames;

    public OutputGuard(AgentPromptFactory promptFactory, List<ToolCallback> toolCallbacks) {
        this.systemPromptText = promptFactory.systemPromptText();
        this.toolNames = toolCallbacks.stream().map(t -> t.getToolDefinition().name()).toList();
    }

    public AgentResponse verify(AgentResponse candidate, List<KnowledgeChunk> ledgerChunks) {
        if (leaksSystemPromptOrTools(candidate.answer())) {
            return refusal(RefusalReason.INJECTION_SUSPECTED);
        }
        if (candidate.refused()) {
            return candidate;
        }
        return verifyGrounded(candidate, ledgerChunks);
    }

    private AgentResponse verifyGrounded(AgentResponse candidate, List<KnowledgeChunk> ledgerChunks) {
        if (candidate.sources().isEmpty()) {
            return refusal(RefusalReason.LOW_CONFIDENCE);
        }
        for (SourceRef source : candidate.sources()) {
            List<KnowledgeChunk> fileChunks = ledgerChunks.stream()
                    .filter(chunk -> chunk.file().equals(source.file()))
                    .toList();
            if (fileChunks.isEmpty()) {
                return refusal(RefusalReason.NO_SOURCE_MATCH);
            }
            boolean excerptFound = fileChunks.stream()
                    .anyMatch(chunk -> normalizeWhitespace(chunk.text())
                            .contains(normalizeWhitespace(source.excerpt())));
            if (!excerptFound) {
                return refusal(RefusalReason.NO_SOURCE_MATCH);
            }
        }
        return withCitationMarker(candidate);
    }

    private AgentResponse withCitationMarker(AgentResponse candidate) {
        List<String> citedFiles = citedFiles(candidate.answer());
        boolean hasValidMarker = candidate.sources().stream().anyMatch(s -> citedFiles.contains(s.file()));
        if (hasValidMarker) {
            return candidate;
        }
        String marker = " [allikas: " + candidate.sources().get(0).file() + "]";
        return new AgentResponse(candidate.answer() + marker, candidate.sources(), candidate.confidence(),
                candidate.refused(), candidate.refusalReason());
    }

    private List<String> citedFiles(String answer) {
        List<String> files = new ArrayList<>();
        Matcher matcher = CITATION_MARKER.matcher(answer);
        while (matcher.find()) {
            files.add(matcher.group(1).trim());
        }
        return files;
    }

    private boolean leaksSystemPromptOrTools(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (toolNames.stream().anyMatch(answer::contains)) {
            return true;
        }
        List<String> answerGrams = sixGrams(answer);
        if (answerGrams.isEmpty()) {
            return false;
        }
        List<String> promptGrams = sixGrams(systemPromptText);
        long overlap = answerGrams.stream().filter(promptGrams::contains).count();
        return (double) overlap / answerGrams.size() > LEAK_THRESHOLD;
    }

    private List<String> sixGrams(String text) {
        String[] tokens = text.toLowerCase().trim().split("\\s+");
        List<String> grams = new ArrayList<>();
        for (int i = 0; i + GRAM_SIZE <= tokens.length; i++) {
            grams.add(String.join(" ", Arrays.asList(tokens).subList(i, i + GRAM_SIZE)));
        }
        return grams;
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private AgentResponse refusal(RefusalReason reason) {
        return new AgentResponse(reason.message(), List.of(), Confidence.LOW, true, reason.message());
    }
}
