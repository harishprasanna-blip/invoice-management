package com.invoicemanagement.validation.infrastructure.sap;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for SAP ERP WebClient.
 * Configures timeouts, authentication, and error handling.
 */
@Configuration
public class SAPWebClientConfiguration {

    @Value("${sap.api.base-url}")
    private String sapBaseUrl;

    @Value("${sap.api.username:#{null}}")
    private String sapUsername;

    @Value("${sap.api.password:#{null}}")
    private String sapPassword;

    @Value("${sap.api.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public WebClient sapWebClient() {
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
            .responseTimeout(Duration.ofSeconds(timeoutSeconds))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

        WebClient.Builder builder = WebClient.builder()
            .baseUrl(sapBaseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(logRequest())
            .filter(logResponse())
            .filter(handleErrors());

        // Add basic auth if configured
        if (sapUsername != null && sapPassword != null) {
            builder.defaultHeaders(headers ->
                headers.setBasicAuth(sapUsername, sapPassword));
        }

        return builder.build();
    }

    /**
     * Log outgoing requests to SAP.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // In production: Use structured logging
            System.out.println("SAP Request: " + clientRequest.method() + " " + clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    /**
     * Log incoming responses from SAP.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            // In production: Use structured logging
            System.out.println("SAP Response Status: " + clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    /**
     * Handle SAP API errors gracefully.
     */
    private ExchangeFilterFunction handleErrors() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                // In production: Log error details and create domain-specific exception
                System.err.println("SAP API Error: " + clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }
}
