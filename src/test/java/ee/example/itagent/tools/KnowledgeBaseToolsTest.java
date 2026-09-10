package ee.example.itagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import ee.example.itagent.kb.KnowledgeBaseIndex;
import ee.example.itagent.kb.KnowledgeBaseLoader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * Phase 3 "done when": exactly three registered tools, getDocument resists
 * traversal input without touching the filesystem, and a direct
 * searchKnowledgeBase call populates the RetrievalLedger.
 */
class KnowledgeBaseToolsTest {

    private KnowledgeBaseLoader loader;
    private RetrievalLedger ledger;
    private KnowledgeBaseTools tools;

    @BeforeEach
    void setUp() {
        loader = new KnowledgeBaseLoader("classpath:kb/");
        KnowledgeBaseIndex index = new KnowledgeBaseIndex(loader, null, false, 4, 0.35, "");
        ledger = new RetrievalLedger();
        tools = new KnowledgeBaseTools(loader, index, ledger);
    }

    @Test
    void toolAllowlist_isExactlyThreeNamedTools() {
        List<ToolCallback> callbacks = new ToolConfig().knowledgeBaseToolCallbacks(tools);

        assertThat(callbacks).hasSize(3);
        assertThat(callbacks.stream().map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("listTopics", "searchKnowledgeBase", "getDocument");
    }

    @Test
    void getDocument_pathTraversalAttempt_returnsNotFound_noFilesystemAccess() {
        KnowledgeBaseTools.DocumentResult result = tools.getDocument("../../../etc/passwd");

        assertThat(result.found()).isFalse();
        assertThat(result.content()).isNull();
    }

    @Test
    void getDocument_unknownFile_returnsNotFound() {
        KnowledgeBaseTools.DocumentResult result = tools.getDocument("does-not-exist.md");

        assertThat(result.found()).isFalse();
    }

    @Test
    void getDocument_knownFile_returnsContentAndRecordsChunksInLedger() {
        KnowledgeBaseTools.DocumentResult result = tools.getDocument("gitlab-access.md");

        assertThat(result.found()).isTrue();
        assertThat(result.content()).isNotBlank();
        assertThat(ledger.chunks()).isNotEmpty();
        assertThat(ledger.chunks()).allMatch(chunk -> chunk.file().equals("gitlab-access.md"));
    }

    @Test
    void searchKnowledgeBase_directCall_populatesLedger() {
        List<KnowledgeBaseTools.ChunkResult> results =
                tools.searchKnowledgeBase("Kuidas taotleda ligipääsu GitLabile?");

        assertThat(results).isNotEmpty();
        assertThat(ledger.chunks()).isNotEmpty();
        assertThat(ledger.chunks().get(0).file()).isEqualTo(results.get(0).file());
    }

    @Test
    void listTopics_returnsAllSevenDocuments_andRecordsThemInLedger() {
        List<KnowledgeBaseTools.TopicSummary> topics = tools.listTopics();

        assertThat(topics).hasSize(7);
        assertThat(ledger.chunks()).hasSize(7);
    }
}
