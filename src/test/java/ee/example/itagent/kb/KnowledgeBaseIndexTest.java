package ee.example.itagent.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives UC-01..UC-05 questions through the retrieval index in lexical-only
 * mode (no OPENAI_API_KEY needed), per IMPLEMENTATION_PLAN.md Phase 2
 * "Done when". Semantic scoring is exercised separately in integration
 * tests, where a live embedding model is available.
 */
class KnowledgeBaseIndexTest {

    private KnowledgeBaseIndex index;
    private KnowledgeBaseLoader loader;

    @BeforeEach
    void setUp() {
        loader = new KnowledgeBaseLoader("classpath:kb/");
        index = new KnowledgeBaseIndex(loader, null, false, 4, 0.35, "");
    }

    @Test
    void indexStartsInLexicalOnlyModeWithoutApiKey() {
        assertThat(index.isSemanticAvailable()).isFalse();
    }

    @Test
    void uc01_directQuestion_topHitIsGitlabAccess() {
        assertTopFile("Kuidas taotleda ligipääsu GitLabile?", "gitlab-access.md");
    }

    @Test
    void uc02_shortInflectedQuery_topHitIsGitlabAccess() {
        assertTopFile("gitlab ligipääs?", "gitlab-access.md");
    }

    @Test
    void uc03_kubernetesQuestion_topHitIsKubernetesDeploy_notGitlab() {
        List<SearchResult> results = index.search("Mis on Kubernetesi deploy protsess?");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunk().file()).isEqualTo("kubernetes-deploy.md");
        assertThat(results.stream().map(r -> r.chunk().file())).doesNotContain("gitlab-access.md");
    }

    @Test
    void uc04_paraphrasedCodeReviewQuestion_topHitIsCodeReview() {
        assertTopFile("Kuidas saan koodi üle vaadata enne merge'i?", "code-review.md");
    }

    @Test
    void uc05_allSevenTopicsAreLoaded() {
        assertThat(loader.documentsByFile()).hasSize(7);
    }

    private void assertTopFile(String query, String expectedFile) {
        List<SearchResult> results = index.search(query);
        assertThat(results).as("search results for '%s'", query).isNotEmpty();
        assertThat(results.get(0).chunk().file()).isEqualTo(expectedFile);
    }
}
