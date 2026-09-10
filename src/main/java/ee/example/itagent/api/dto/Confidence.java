package ee.example.itagent.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Confidence {
    HIGH, MEDIUM, LOW;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    /**
     * The model's structured-output schema lists the raw enum constant names
     * (HIGH/MEDIUM/LOW); it does not reliably follow {@link #toJson()}'s
     * lowercase form. Accept either case rather than fail the whole request.
     */
    @JsonCreator
    public static Confidence fromJson(String value) {
        return Confidence.valueOf(value.trim().toUpperCase());
    }
}
