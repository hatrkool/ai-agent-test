package ee.example.itagent.api.dto;

import java.util.List;

public record ApiError(String error, List<String> details) {
}
