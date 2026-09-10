package ee.example.itagent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ee.example.itagent.api.dto.AgentResponse;
import org.junit.jupiter.api.Test;

/**
 * API-04 (plan §10 / ai-developer-task.md §7.2): a valid request through a
 * real OpenAI round trip returns every field the API contract promises.
 */
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void api04_validRequest_returnsAllContractFieldsWithSourceDetailAndCitation() {
        AgentResponse response = ask("Kuidas taotleda ligipääsu GitLabile?");

        assertThat(response.answer()).isNotBlank();
        assertThat(response.sources()).isNotEmpty();
        response.sources().forEach(source -> {
            assertThat(source.file()).isNotBlank();
            assertThat(source.excerpt()).isNotBlank();
        });
        assertThat(response.confidence()).isNotNull();
        assertThat(response.refused()).isFalse();
        assertThat(response.answer()).contains("[allikas:");
    }
}
