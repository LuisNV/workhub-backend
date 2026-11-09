package com.thanh.workhub.platform.storage;

import java.util.Map;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@ConditionalOnProperty(name = "app.cloudinary.enabled", havingValue = "true")
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(
            @Value("${app.cloudinary.cloud-name}") String cloudName,
            @Value("${app.cloudinary.api-key}") String apiKey,
            @Value("${app.cloudinary.api-secret}") String apiSecret) {
        Assert.hasText(cloudName, "CLOUDINARY_CLOUD_NAME must be configured");
        Assert.hasText(apiKey, "CLOUDINARY_API_KEY must be configured");
        Assert.hasText(apiSecret, "CLOUDINARY_API_SECRET must be configured");
        return new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }
}
