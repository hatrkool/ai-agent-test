package ee.example.itagent.guard;

import static org.assertj.core.api.Assertions.assertThat;

import ee.example.itagent.guard.SensitiveDataPatterns.Detector;
import org.junit.jupiter.api.Test;

class SensitiveDataScrubberTest {

    private final SensitiveDataScrubber scrubber = new SensitiveDataScrubber();

    @Test
    void data01_isikukoodInQuestion_isRedactedAndRequestContinues() {
        SensitiveDataScrubber.Result result =
                scrubber.scrub("Minu isikukood on 47101010000, kas saan VPN ligipääsu?");

        assertThat(result.refuse()).isFalse();
        assertThat(result.scrubbedText()).doesNotContain("47101010000");
        assertThat(result.scrubbedText()).contains("[isikukood eemaldatud]");
        assertThat(result.matchedDetectors()).contains(Detector.ISIKUKOOD);
    }

    @Test
    void data02_apiKeyInQuestion_refusesBeforeAnyModelCall() {
        SensitiveDataScrubber.Result result =
                scrubber.scrub("Minu võti on sk-abcdefghijklmnopqrstuvwx, palun aita");

        assertThat(result.refuse()).isTrue();
        assertThat(result.matchedDetectors()).contains(Detector.PROVIDER_KEY);
    }

    @Test
    void data02_passwordDisclosure_refuses() {
        SensitiveDataScrubber.Result result = scrubber.scrub("parool: hunter2");

        assertThat(result.refuse()).isTrue();
        assertThat(result.matchedDetectors()).contains(Detector.PASSWORD_DISCLOSURE);
    }

    @Test
    void ibanInQuestion_isRedactedAndRequestContinues() {
        SensitiveDataScrubber.Result result = scrubber.scrub("Minu konto EE123456789012345678 vajab kontrolli");

        assertThat(result.refuse()).isFalse();
        assertThat(result.scrubbedText()).contains("[konto eemaldatud]");
    }

    @Test
    void cleanQuestion_isUnchanged() {
        SensitiveDataScrubber.Result result = scrubber.scrub("Kuidas taotleda GitLabi ligipääsu?");

        assertThat(result.refuse()).isFalse();
        assertThat(result.scrubbedText()).isEqualTo("Kuidas taotleda GitLabi ligipääsu?");
        assertThat(result.matchedDetectors()).isEmpty();
    }
}
