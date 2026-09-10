package ee.example.itagent.kb;

public record KnowledgeChunk(
        String id,
        String file,
        String docTitle,
        String sectionHeading,
        String text) {
}
