package ee.example.itagent.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Confidence {
    HIGH, MEDIUM, LOW;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
