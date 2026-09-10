package ee.example.itagent.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bucket4j, in-memory, one bucket per client IP. Scoped to the ask endpoint
 * only so /api/v1/health stays unaffected (plan §8 guard pipeline).
 *
 * <p>{@code @EnableConfigurationProperties} here (in addition to
 * {@code @ConfigurationPropertiesScan} on the application class) is
 * deliberate: {@code Filter} beans are always pulled into a
 * {@code @WebMvcTest} slice, but the scan-based registration is not, so
 * without this the slice fails to wire AgentProperties.
 */
@Component
@EnableConfigurationProperties(AgentProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ASK_PATH = "/api/v1/agent/ask";

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int requestsPerMinute;

    public RateLimitFilter(AgentProperties properties) {
        this.enabled = properties.rateLimit().enabled();
        this.requestsPerMinute = properties.rateLimit().requestsPerMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !ASK_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), key -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\"}");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }
}
