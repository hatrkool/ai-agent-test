package ee.example.itagent.api.dto;

public enum RefusalReason {
    OUT_OF_SCOPE,
    SENSITIVE_REQUEST,
    SENSITIVE_DATA_IN_INPUT,
    NO_SOURCE_MATCH,
    INJECTION_SUSPECTED,
    LOW_CONFIDENCE,
    CLARIFICATION_NEEDED
}
