package ee.example.itagent.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the system prompt from its own file — never a Java string literal,
 * see CLAUDE.md invariant 8 — and exposes both the {@link Resource} (for
 * ChatClient's defaultSystem) and its text (for OutputGuard's leak check).
 */
@Component
public class AgentPromptFactory {

    private final Resource systemPromptResource;
    private final String systemPromptText;

    public AgentPromptFactory(@Value("classpath:prompts/system-prompt.txt") Resource systemPromptResource) {
        this.systemPromptResource = systemPromptResource;
        try {
            this.systemPromptText = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read system prompt", e);
        }
    }

    public Resource systemPromptResource() {
        return systemPromptResource;
    }

    public String systemPromptText() {
        return systemPromptText;
    }
}
