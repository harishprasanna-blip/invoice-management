package com.invoicemanagement.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for ValidationContext.
 * Bounded Context: ValidationContext
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@ComponentScan(basePackages = {
    "com.invoicemanagement.validation",
    "com.invoicemanagement.sharedkernel"
})
public class ValidationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidationApplication.class, args);
    }
}
