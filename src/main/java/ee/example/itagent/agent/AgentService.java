package ee.example.itagent.agent;

import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.Confidence;
import ee.example.itagent.api.dto.RefusalReason;
import ee.example.itagent.guard.InputGuard;
import ee.example.itagent.guard.OutputGuard;
import ee.example.itagent.guard.SafeLogging;
import ee.example.itagent.guard.SensitiveDataScrubber;
import ee.example.itagent.tools.RetrievalLedger;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the fixed guard pipeline (CLAUDE.md: "Guard pipeline order is
 * fixed") around the model call: InputGuard, then SensitiveDataScrubber,
 * then ChatClient + tools, then OutputGuard. A pre-model refusal
 * short-circuits before any OpenAI call.
 */
@Service
public class AgentService {

    private static final String DEFAULT_CONVERSATION_ID = "anonymous";

    private final ChatClient chatClient;
    private final RetrievalLedger ledger;
    private final InputGuard inputGuard;
    private final SensitiveDataScrubber scrubber;
    private final OutputGuard outputGuard;
    private final SafeLogging safeLogging;

    public AgentService(
            ChatClient.Builder chatClientBuilder,
            AgentPromptFactory promptFactory,
            List<ToolCallback> toolCallbacks,
            ChatMemory chatMemory,
            RetrievalLedger ledger,
            InputGuard inputGuard,
            SensitiveDataScrubber scrubber,
            OutputGuard outputGuard,
            SafeLogging safeLogging) {
        this.chatClient = chatClientBuilder
                .defaultSystem(promptFactory.systemPromptResource())
                .defaultTools(toolCallbacks.toArray())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.ledger = ledger;
        this.inputGuard = inputGuard;
        this.scrubber = scrubber;
        this.outputGuard = outputGuard;
        this.safeLogging = safeLogging;
    }

    public AgentResponse answer(String question, String sessionId) {
        String conversationId = (sessionId == null || sessionId.isBlank()) ? DEFAULT_CONVERSATION_ID : sessionId;
        safeLogging.logRequest(conversationId, question);

        Optional<String> injectionLabel = inputGuard.matchLabel(question);
        if (injectionLabel.isPresent()) {
            safeLogging.logInjectionMatch(conversationId, injectionLabel.get());
            return refusal(RefusalReason.INJECTION_SUSPECTED);
        }

        SensitiveDataScrubber.Result scrubResult = scrubber.scrub(question);
        if (!scrubResult.matchedDetectors().isEmpty()) {
            safeLogging.logSensitiveDataMatch(conversationId, scrubResult.matchedDetectors());
        }
        if (scrubResult.refuse()) {
            return refusal(RefusalReason.SENSITIVE_DATA_IN_INPUT);
        }

        AgentResponse modelResponse = chatClient.prompt()
                .user(scrubResult.scrubbedText())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(AgentResponse.class);

        return outputGuard.verify(modelResponse, ledger.chunks());
    }

    private AgentResponse refusal(RefusalReason reason) {
        return new AgentResponse(reason.message(), List.of(), Confidence.LOW, true, reason.message());
    }
}
