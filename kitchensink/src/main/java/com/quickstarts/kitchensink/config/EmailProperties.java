package com.quickstarts.kitchensink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.mail")
public class EmailProperties {
    /**
     * From address shown to the recipient.
     * Defaults to spring.mail.username if not set.
     */
    private String from;

    /**
     * How admin recipients are resolved: REPO (query Role.ADMIN users) or STATIC (use emails list).
     */
    private NotifyMode notifyMode = NotifyMode.REPO;

    /**
     * Used when notifyMode = STATIC, e.g., ["admin1@x.test","admin2@x.test"]
     */
    private List<String> emails;

    /**
     * Base URL used to build approve/reject links in admin emails.
     * e.g., http://localhost:8080 (dev) or https://admin.example.com (prod)
     */
    private String approveBaseUrl = "http://localhost:8080";

    /**
     * Login URL used in the welcome email's Sign in button.
     */
    private String loginUrl = "http://localhost:8080/auth/login";

    public enum NotifyMode { REPO, STATIC }
}

