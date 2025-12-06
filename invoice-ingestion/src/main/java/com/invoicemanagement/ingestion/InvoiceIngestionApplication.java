package com.invoicemanagement.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application for Invoice Ingestion Service.
 * Bounded Context: InvoiceIngestionContext
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.invoicemanagement.ingestion",
    "com.invoicemanagement.sharedkernel"
})
public class InvoiceIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceIngestionApplication.class, args);
    }
}
