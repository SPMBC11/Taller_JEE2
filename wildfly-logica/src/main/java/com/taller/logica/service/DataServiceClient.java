package com.taller.logica.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taller.logica.dto.PersistEvaluationRequest;
import com.taller.logica.dto.PersistEvaluationResponse;
import jakarta.ejb.Stateless;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Stateless
public class DataServiceClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public PersistEvaluationResponse persistEvaluation(PersistEvaluationRequest request) {
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(request);
        } catch (IOException exception) {
            throw new RuntimeException("No fue posible serializar la evaluacion", exception);
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(resolveDataServiceUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Error en wildfly-datos: HTTP " + response.statusCode() + " - " + response.body());
            }
            return OBJECT_MAPPER.readValue(response.body(), PersistEvaluationResponse.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("No fue posible invocar wildfly-datos", exception);
        } catch (IOException exception) {
            throw new RuntimeException("No fue posible invocar wildfly-datos", exception);
        }
    }

    private String resolveDataServiceUrl() {
        String env = System.getenv("DATA_SERVICE_URL");
        if (env == null || env.isBlank()) {
            return "http://wildfly-datos:8080/api/data/evaluations";
        }
        return env;
    }
}
