package ee.example.itagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import ee.example.itagent.config.AgentProperties.Kb;
import ee.example.itagent.config.AgentProperties.Logging;
import ee.example.itagent.config.AgentProperties.RateLimit;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void eleventhRequestInAMinute_returns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(true, 10));
        AtomicInteger passedThrough = new AtomicInteger();
        FilterChain chain = (req, res) -> passedThrough.incrementAndGet();

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/ask");
            request.setRemoteAddr("10.0.0.1");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(request, lastResponse, chain);
        }

        assertThat(passedThrough.get()).isEqualTo(10);
        assertThat(lastResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void disabled_neverLimits() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(false, 1));
        AtomicInteger passedThrough = new AtomicInteger();
        FilterChain chain = (req, res) -> passedThrough.incrementAndGet();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/ask");
            request.setRemoteAddr("10.0.0.2");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        assertThat(passedThrough.get()).isEqualTo(5);
    }

    @Test
    void unrelatedPath_isNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(true, 1));
        AtomicInteger passedThrough = new AtomicInteger();
        FilterChain chain = (req, res) -> passedThrough.incrementAndGet();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
            request.setRemoteAddr("10.0.0.3");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        assertThat(passedThrough.get()).isEqualTo(5);
    }

    private AgentProperties properties(boolean rateLimitEnabled, int requestsPerMinute) {
        return new AgentProperties(2000, Duration.ofMinutes(30), 1000,
                new Kb("classpath:kb/", false, 4, 0.35),
                new RateLimit(rateLimitEnabled, requestsPerMinute),
                new Logging(false));
    }
}
