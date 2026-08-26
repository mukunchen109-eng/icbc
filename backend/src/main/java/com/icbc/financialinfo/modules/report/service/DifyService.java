package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.model.DifyNewsArticleInput;
import com.icbc.financialinfo.modules.report.model.DifyWorkflowRequest;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DifyService {

    private static final Logger log = LoggerFactory.getLogger(DifyService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final int PREVIEW_LIMIT = 500;
    private static final String REQUEST_FILE_NAME = "dify-request.json";

    private final RestClient.Builder restClientBuilder;
    private final ReportProperties reportProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DifyService(RestClient.Builder restClientBuilder, ReportProperties reportProperties) {
        this.restClientBuilder = restClientBuilder;
        this.reportProperties = reportProperties;
    }

    public String generateDailySummary(DifyWorkflowRequest workflowRequest, Path reportDirectory) {
        ReportProperties.Dify dify = reportProperties.getDify();
        validateConfig(dify);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputs", workflowRequest.toInputs());
        payload.put("response_mode", dify.getResponseMode());
        payload.put("user", dify.getUser());

        writePayloadFile(reportDirectory, payload);

        if (reportProperties.getDify().isMockEnabled()) {
            log.info("Dify mock mode enabled, skipping real workflow call");
            return buildMockReport(workflowRequest);
        }

        log.info(
                "Calling Dify workflow: baseUrl={}, endpoint={}, responseMode={}, user={}, inputsKeys={}, articlesCount={}, articlesPreview={}",
                maskUrl(dify.getBaseUrl()),
                dify.getEndpoint(),
                dify.getResponseMode(),
                dify.getUser(),
                workflowRequest.toInputs().keySet(),
                workflowRequest.articles().size(),
                previewArticles(workflowRequest.articles())
        );

        try {
            Map<String, Object> response = restClientBuilder
                    .baseUrl(trimTrailingSlash(dify.getBaseUrl()))
                    .defaultHeader("Authorization", "Bearer " + dify.getApiKey())
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri(dify.getEndpoint())
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);

            String content = extractContent(response);
            log.info(
                    "Dify workflow call succeeded: workflowRunId={}, taskId={}, resultLength={}, responseTopLevelKeys={}",
                    getStringValue(response, "workflow_run_id"),
                    getStringValue(response, "task_id"),
                    content.length(),
                    response == null ? List.of() : response.keySet()
            );
            return content;
        } catch (RestClientResponseException ex) {
            log.error(
                    "Dify workflow HTTP error: status={}, statusText={}, baseUrl={}, endpoint={}, responseBody={}",
                    ex.getRawStatusCode(),
                    ex.getStatusText(),
                    maskUrl(dify.getBaseUrl()),
                    dify.getEndpoint(),
                    truncate(ex.getResponseBodyAsString(), 2000),
                    ex
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Dify Workflow HTTP error: status=" + ex.getRawStatusCode() + ", " + ex.getStatusText(),
                    ex
            );
        } catch (RestClientException ex) {
            log.error(
                    "Dify workflow request failed: baseUrl={}, endpoint={}, responseMode={}, user={}, message={}",
                    maskUrl(dify.getBaseUrl()),
                    dify.getEndpoint(),
                    dify.getResponseMode(),
                    dify.getUser(),
                    ex.getMessage(),
                    ex
            );
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dify workflow call failed: " + ex.getMessage(), ex);
        }
    }

    private void validateConfig(ReportProperties.Dify dify) {
        if (!StringUtils.hasText(dify.getBaseUrl())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify is not configured, please set app.report.dify.base-url"
            );
        }
        if (!StringUtils.hasText(dify.getApiKey())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify is not configured, please set app.report.dify.api-key"
            );
        }
        if (!StringUtils.hasText(dify.getEndpoint())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify is not configured, please set app.report.dify.endpoint"
            );
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dify workflow returned an empty response");
        }

        Object taskId = response.get("task_id");
        Object workflowRunId = response.get("workflow_run_id");
        Object data = response.get("data");

        if (data instanceof Map<?, ?> dataMap) {
            Object status = dataMap.get("status");
            if (status instanceof String workflowStatus && "failed".equalsIgnoreCase(workflowStatus)) {
                Object error = dataMap.get("error");
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Dify workflow execution failed" + buildTraceSuffix(taskId, workflowRunId)
                                + (error == null ? "" : ", error=" + error)
                );
            }

            Object outputs = dataMap.get("outputs");
            if (outputs instanceof Map<?, ?> outputMap) {
                for (String key : new String[]{"result", "report", "content", "text", "answer"}) {
                    Object value = outputMap.get(key);
                    if (value instanceof String content && StringUtils.hasText(content)) {
                        return content.trim();
                    }
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Cannot parse Dify workflow response" + buildTraceSuffix(taskId, workflowRunId)
        );
    }

    private void writePayloadFile(Path reportDirectory, Map<String, Object> payload) {
        try {
            Files.createDirectories(reportDirectory);
            Path outputFile = reportDirectory.resolve(REQUEST_FILE_NAME);
            String formattedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(outputFile, formattedJson, StandardCharsets.UTF_8);
            log.info("Dify request payload written to {}", outputFile.toAbsolutePath());
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to write Dify request payload file",
                    ex
            );
        }
    }

    private String buildMockReport(DifyWorkflowRequest workflowRequest) {
        List<DifyNewsArticleInput> articles = workflowRequest.articles();
        StringBuilder builder = new StringBuilder();
        builder.append("Article count: ").append(articles.size()).append(System.lineSeparator());
        builder.append(System.lineSeparator());
        builder.append("Articles:").append(System.lineSeparator());

        int summaryCount = Math.min(5, articles.size());
        for (int i = 0; i < summaryCount; i++) {
            DifyNewsArticleInput article = articles.get(i);
            builder.append(i + 1).append(". ")
                    .append(article.title())
                    .append(" | id=").append(article.id())
                    .append(" | industry=").append(article.industry())
                    .append(" | area=").append(article.area())
                    .append(System.lineSeparator());
        }

        return builder.toString().trim();
    }

    private String buildTraceSuffix(Object taskId, Object workflowRunId) {
        StringBuilder builder = new StringBuilder();
        if (taskId != null) {
            builder.append(", task_id=").append(taskId);
        }
        if (workflowRunId != null) {
            builder.append(", workflow_run_id=").append(workflowRunId);
        }
        return builder.toString();
    }

    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        int schemeIndex = trimmed.indexOf("://");
        if (schemeIndex < 0) {
            return trimmed;
        }
        int pathIndex = trimmed.indexOf('/', schemeIndex + 3);
        return pathIndex > 0 ? trimmed.substring(0, pathIndex) : trimmed;
    }

    private String previewArticles(List<DifyNewsArticleInput> articles) {
        if (articles == null || articles.isEmpty()) {
            return "[]";
        }
        int previewSize = Math.min(3, articles.size());
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < previewSize; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            DifyNewsArticleInput article = articles.get(i);
            builder.append("{id=").append(preview(article.id()))
                    .append(", title=").append(preview(article.title()))
                    .append(", industry=").append(preview(article.industry()))
                    .append(", area=").append(preview(article.area()))
                    .append("}");
        }
        if (articles.size() > previewSize) {
            builder.append(", ...");
        }
        builder.append("]");
        return builder.toString();
    }

    private String preview(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LIMIT ? normalized : normalized.substring(0, PREVIEW_LIMIT) + "...";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
