package com.invoicemanagement.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot Application for Payment Orchestration Service.
 * 
 * Implements Saga orchestration pattern for distributed payment processing.
 * Coordinates transactions across ValidationContext, SAP ERP, and Payment Gateway.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.invoicemanagement.payment",
    "com.invoicemanagement.sharedkernel"
})
public class PaymentOrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentOrchestrationApplication.class, args);
    }
}
