package ee.example.itagent.agent;

import ee.example.itagent.config.AgentProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat memory keyed on sessionId, bounded by agent.session-ttl and
 * agent.max-sessions. Overrides Spring AI's autoconfigured in-memory
 * {@code ChatMemoryRepository} (unbounded, no TTL) with one that evicts.
 */
@Configuration
public class SessionMemoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(AgentProperties properties) {
        return new TtlChatMemoryRepository(properties.sessionTtl(), properties.maxSessions());
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }

    private static final class TtlChatMemoryRepository implements ChatMemoryRepository {

        private record Entry(List<Message> messages, Instant lastAccess) {
        }

        private final Duration ttl;
        private final int maxSessions;
        private final Map<String, Entry> store;

        TtlChatMemoryRepository(Duration ttl, int maxSessions) {
            this.ttl = ttl;
            this.maxSessions = maxSessions;
            this.store = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > TtlChatMemoryRepository.this.maxSessions;
                }
            };
        }

        @Override
        public synchronized List<String> findConversationIds() {
            evictExpired();
            return List.copyOf(store.keySet());
        }

        @Override
        public synchronized List<Message> findByConversationId(String conversationId) {
            evictExpired();
            Entry entry = store.get(conversationId);
            return entry == null ? List.of() : entry.messages();
        }

        @Override
        public synchronized void saveAll(String conversationId, List<Message> messages) {
            store.put(conversationId, new Entry(List.copyOf(messages), Instant.now()));
        }

        @Override
        public synchronized void deleteByConversationId(String conversationId) {
            store.remove(conversationId);
        }

        private void evictExpired() {
            Instant cutoff = Instant.now().minus(ttl);
            store.entrySet().removeIf(e -> e.getValue().lastAccess().isBefore(cutoff));
        }
    }
}
