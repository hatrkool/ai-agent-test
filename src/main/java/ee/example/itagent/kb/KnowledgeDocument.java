package ee.example.itagent.kb;

import java.util.List;

public record KnowledgeDocument(
        String file,
        String title,
        String topic,
        List<String> aliases,
        List<KnowledgeChunk> chunks) {
}
