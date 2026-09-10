package ee.example.itagent.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.example.itagent.agent.AgentPromptFactory;
import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.Confidence;
import ee.example.itagent.api.dto.SourceRef;
import ee.example.itagent.kb.KnowledgeChunk;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;

class OutputGuardTest {

    private static final String SYSTEM_PROMPT = "Oled sisemine IT teenuste info agent. Vasta ainult eesti keeles.";

    private final AgentPromptFactory promptFactory =
            new AgentPromptFactory(new ByteArrayResource(SYSTEM_PROMPT.getBytes(StandardCharsets.UTF_8)));
    private final OutputGuard outputGuard = new OutputGuard(promptFactory,
            List.of(fakeTool("listTopics"), fakeTool("searchKnowledgeBase"), fakeTool("getDocument")));

    private final List<KnowledgeChunk> ledger = List.of(
            new KnowledgeChunk("gitlab-access.md#ligipaasu-taotlemine", "gitlab-access.md", "GitLab ligipääs",
                    "Ligipääsu taotlemine", "Logi sisse teenuste portaali ja vali GitLab ligipääsutaotlus."));

    @Test
    void fabricatedFile_isRefused() {
        AgentResponse candidate = new AgentResponse("Vastus [allikas: unknown-file.md]",
                List.of(new SourceRef("unknown-file.md", "Tundmatu", "Logi sisse teenuste portaali")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isTrue();
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void fabricatedExcerpt_isRefused() {
        AgentResponse candidate = new AgentResponse("Vastus [allikas: gitlab-access.md]",
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "See tekst ei ole dokumendis")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isTrue();
    }

    @Test
    void emptySourcesWithRefusedFalse_isRefused() {
        AgentResponse candidate = new AgentResponse("Vastus", List.of(), Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isTrue();
        assertThat(result.refusalReason()).isNotBlank();
    }

    @Test
    void validSourcesWithoutMarker_getsMarkerAppended() {
        AgentResponse candidate = new AgentResponse("Logi sisse teenuste portaali ja vali GitLab ligipääsutaotlus.",
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "Logi sisse teenuste portaali")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isFalse();
        assertThat(result.answer()).contains("[allikas: gitlab-access.md]");
    }

    @Test
    void validSourcesWithMarker_isUnchanged() {
        String answer = "Logi sisse teenuste portaali ja vali GitLab ligipääsutaotlus. [allikas: gitlab-access.md]";
        AgentResponse candidate = new AgentResponse(answer,
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "Logi sisse teenuste portaali")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isFalse();
        assertThat(result.answer()).isEqualTo(answer);
    }

    @Test
    void refusedResponse_passesThroughWithoutSourceChecks() {
        AgentResponse candidate = new AgentResponse("Vabandust, ma ei saa sellele vastata.",
                List.of(), Confidence.LOW, true, "Küsimus on väljaspool IT teenuste teemat.");

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result).isEqualTo(candidate);
    }

    @Test
    void answerLeakingSystemPromptWording_isRefused() {
        AgentResponse candidate = new AgentResponse(
                "Oled sisemine IT teenuste info agent. Vasta ainult eesti keeles. [allikas: gitlab-access.md]",
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "Logi sisse teenuste portaali")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isTrue();
    }

    @Test
    void answerContainingToolName_isRefused() {
        AgentResponse candidate = new AgentResponse(
                "Kasutasin tööriista searchKnowledgeBase et see leida. [allikas: gitlab-access.md]",
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "Logi sisse teenuste portaali")),
                Confidence.HIGH, false, null);

        AgentResponse result = outputGuard.verify(candidate, ledger);

        assertThat(result.refused()).isTrue();
    }

    private static ToolCallback fakeTool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(tool.getToolDefinition()).thenReturn(definition);
        return tool;
    }
}
