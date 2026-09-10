package ee.example.itagent.kb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval over the knowledge base: lexical token-overlap (with an
 * alias boost) combined with OpenAI-embedding cosine similarity in a
 * {@link SimpleVectorStore}. See IMPLEMENTATION_PLAN.md §5.
 *
 * Degrades to lexical-only when semantic search is disabled, no API key is
 * configured, or the embedding call fails at startup — this keeps unit
 * tests and /health working without OPENAI_API_KEY (plan §5, "Startup
 * without a key").
 */
@Component
public class KnowledgeBaseIndex {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIndex.class);
    private static final double ALIAS_BOOST = 0.4;
    private static final double LEXICAL_WEIGHT = 0.4;
    private static final double SEMANTIC_WEIGHT = 0.6;

    private final KnowledgeBaseLoader loader;
    private final int topK;
    private final double scoreThreshold;
    private final Map<String, Set<String>> chunkTokens = new LinkedHashMap<>();
    private final Map<String, List<String>> documentAliases = new LinkedHashMap<>();

    private VectorStore vectorStore;
    private boolean semanticAvailable = false;

    public KnowledgeBaseIndex(
            KnowledgeBaseLoader loader,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            @Value("${agent.kb.semantic-search-enabled:true}") boolean semanticSearchEnabled,
            @Value("${agent.kb.top-k:4}") int topK,
            @Value("${agent.kb.score-threshold:0.35}") double scoreThreshold,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.loader = loader;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;

        for (KnowledgeDocument doc : loader.documentsByFile().values()) {
            List<String> foldedAliases = doc.aliases().stream()
                    .map(EstonianTextNormalizer::foldAndLowercase)
                    .toList();
            documentAliases.put(doc.file(), foldedAliases);
            for (KnowledgeChunk chunk : doc.chunks()) {
                chunkTokens.put(chunk.id(),
                        EstonianTextNormalizer.normalizedTokens(chunk.sectionHeading() + " " + chunk.text()));
            }
        }

        if (semanticSearchEnabled && !openAiApiKey.isBlank()) {
            EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel != null) {
                try {
                    initSemanticIndex(embeddingModel);
                } catch (Exception e) {
                    log.warn("Semantic search disabled: failed to build embedding index ({})", e.getMessage());
                }
            }
        }
        if (!semanticAvailable) {
            log.info("Knowledge base index running in lexical-only mode");
        }
    }

    private void initSemanticIndex(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = loader.allChunks().stream()
                .map(chunk -> Document.builder()
                        .id(chunk.id())
                        .text(chunk.text())
                        .metadata("file", chunk.file())
                        .metadata("heading", chunk.sectionHeading())
                        .build())
                .toList();
        store.add(documents);
        this.vectorStore = store;
        this.semanticAvailable = true;
    }

    public boolean isSemanticAvailable() {
        return semanticAvailable;
    }

    public List<SearchResult> search(String query) {
        Set<String> queryTokens = EstonianTextNormalizer.normalizedTokens(query);
        String foldedQuery = EstonianTextNormalizer.foldAndLowercase(query);
        Map<String, Double> semanticScores = semanticAvailable ? semanticScores(query) : Map.of();

        List<SearchResult> results = new ArrayList<>();
        for (KnowledgeDocument doc : loader.documentsByFile().values()) {
            boolean aliasHit = documentAliases.get(doc.file()).stream().anyMatch(foldedQuery::contains);
            for (KnowledgeChunk chunk : doc.chunks()) {
                double lexical = EstonianTextNormalizer.overlapScore(queryTokens, chunkTokens.get(chunk.id()));
                if (aliasHit) {
                    lexical = Math.min(1.0, lexical + ALIAS_BOOST);
                }
                double semantic = semanticScores.getOrDefault(chunk.id(), 0.0);
                double score = semanticAvailable ? (LEXICAL_WEIGHT * lexical + SEMANTIC_WEIGHT * semantic) : lexical;
                if (score >= scoreThreshold) {
                    results.add(new SearchResult(chunk, score));
                }
            }
        }
        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    private Map<String, Double> semanticScores(String query) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(topK, loader.allChunks().size()))
                    .similarityThreshold(0.0)
                    .build();
            List<Document> found = vectorStore.similaritySearch(request);
            Map<String, Double> scores = new LinkedHashMap<>();
            for (Document d : found) {
                Double score = d.getScore();
                scores.put(d.getId(), score == null ? 0.0 : score);
            }
            return scores;
        } catch (Exception e) {
            log.warn("Semantic search failed at query time, falling back to lexical only: {}", e.getMessage());
            return Map.of();
        }
    }
}
