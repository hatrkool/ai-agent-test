package ee.example.itagent.tools;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link KnowledgeBaseTools}'s {@code @Tool} methods as explicit
 * {@link ToolCallback} beans, passed to the {@code ChatClient} in Phase 4.
 * No general-purpose tool is registered anywhere — this is the entire set.
 */
@Configuration
public class ToolConfig {

    @Bean
    public List<ToolCallback> knowledgeBaseToolCallbacks(KnowledgeBaseTools knowledgeBaseTools) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeBaseTools)
                .build()
                .getToolCallbacks();
        return List.of(callbacks);
    }
}
