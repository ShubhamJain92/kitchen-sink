package com.kitchensink;

import com.kitchensink.core.notification.email.service.EmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"com.kitchensink", "com.kitchensink.persistence"})
@EnableMongoRepositories(basePackages = "com.kitchensink.persistence")
@EnableConfigurationProperties(EmailProperties.class)
public class KitchensinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchensinkApplication.class, args);
    }
}
