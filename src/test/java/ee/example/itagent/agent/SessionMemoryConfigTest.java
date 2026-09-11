package ee.example.itagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ee.example.itagent.config.AgentProperties;
import ee.example.itagent.config.AgentProperties.Kb;
import ee.example.itagent.config.AgentProperties.Logging;
import ee.example.itagent.config.AgentProperties.RateLimit;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * {@code TtlChatMemoryRepository} (SessionMemoryConfig's private nested
 * implementation) has no coverage elsewhere: live integration tests can't
 * practically wait out a 30-minute TTL or create 1000+ sessions to prove the
 * cap. Exercised here through the public {@link ChatMemoryRepository}
 * contract the bean method returns.
 */
class SessionMemoryConfigTest {

    private final SessionMemoryConfig config = new SessionMemoryConfig();

    @Test
    void savedSessionIsReadableBeforeItExpires() {
        ChatMemoryRepository repository = config.chatMemoryRepository(properties(Duration.ofMinutes(30), 1000));

        repository.saveAll("session-1", List.of(new UserMessage("Kuidas saada GitLabi ligipääsu?")));

        assertThat(repository.findByConversationId("session-1")).hasSize(1);
        assertThat(repository.findConversationIds()).containsExactly("session-1");
    }

    @Test
    void sessionExpiresAfterItsTtlElapses() throws InterruptedException {
        ChatMemoryRepository repository = config.chatMemoryRepository(properties(Duration.ofMillis(20), 1000));

        repository.saveAll("session-1", List.of(new UserMessage("Kuidas saada GitLabi ligipääsu?")));
        Thread.sleep(60);

        assertThat(repository.findByConversationId("session-1")).isEmpty();
        assertThat(repository.findConversationIds()).isEmpty();
    }

    @Test
    void oldestSessionIsEvictedWhenMaxSessionsIsExceeded() {
        ChatMemoryRepository repository = config.chatMemoryRepository(properties(Duration.ofMinutes(30), 2));

        repository.saveAll("session-1", List.of(new UserMessage("esimene")));
        repository.saveAll("session-2", List.of(new UserMessage("teine")));
        repository.saveAll("session-3", List.of(new UserMessage("kolmas")));

        assertThat(repository.findByConversationId("session-1")).isEmpty();
        assertThat(repository.findByConversationId("session-2")).hasSize(1);
        assertThat(repository.findByConversationId("session-3")).hasSize(1);
    }

    @Test
    void deleteRemovesTheSession() {
        ChatMemoryRepository repository = config.chatMemoryRepository(properties(Duration.ofMinutes(30), 1000));

        repository.saveAll("session-1", List.of(new UserMessage("kustutamiseks")));
        repository.deleteByConversationId("session-1");

        assertThat(repository.findByConversationId("session-1")).isEmpty();
    }

    private AgentProperties properties(Duration sessionTtl, int maxSessions) {
        return new AgentProperties(2000, sessionTtl, maxSessions,
                new Kb("classpath:kb/", false, 4, 0.35),
                new RateLimit(false, 10),
                new Logging(false));
    }
}
