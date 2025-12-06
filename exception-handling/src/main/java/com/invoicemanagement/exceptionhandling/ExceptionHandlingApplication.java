package com.invoicemanagement.exceptionhandling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for ExceptionHandlingContext.
 * Bounded Context: ExceptionHandlingContext
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "com.invoicemanagement.exceptionhandling",
    "com.invoicemanagement.sharedkernel"
})
public class ExceptionHandlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionHandlingApplication.class, args);
    }
}
