package ee.example.itagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ItAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItAgentApplication.class, args);
    }
}
