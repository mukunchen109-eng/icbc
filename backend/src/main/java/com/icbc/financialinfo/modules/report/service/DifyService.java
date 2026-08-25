package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.model.DifyWorkflowRequest;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DifyService {

    private static final Logger log = LoggerFactory.getLogger(DifyService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final int PREVIEW_LIMIT = 500;

    private final RestClient.Builder restClientBuilder;
    private final ReportProperties reportProperties;

    public DifyService(RestClient.Builder restClientBuilder, ReportProperties reportProperties) {
        this.restClientBuilder = restClientBuilder;
        this.reportProperties = reportProperties;
    }

    public String generateDailySummary(DifyWorkflowRequest workflowRequest) {
        if (reportProperties.getDify().isMockEnabled()) {
            log.info("Dify mock mode enabled, skipping real workflow call");
            return buildMockReport(workflowRequest);
        }

        ReportProperties.Dify dify = reportProperties.getDify();
        validateConfig(dify);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputs", workflowRequest.toInputs());
        payload.put("response_mode", dify.getResponseMode());
        payload.put("user", dify.getUser());

        log.info(
                "Calling Dify workflow: baseUrl={}, endpoint={}, responseMode={}, user={}, inputsKeys={}, titleLength={}, contentLength={}, contentPreview={}",
                maskUrl(dify.getBaseUrl()),
                dify.getEndpoint(),
                dify.getResponseMode(),
                dify.getUser(),
                workflowRequest.toInputs().keySet(),
                lengthOf(workflowRequest.title()),
                lengthOf(workflowRequest.content()),
                preview(workflowRequest.content())
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "调用 Dify Workflow 失败: " + ex.getMessage(), ex);
        }
    }

    private void validateConfig(ReportProperties.Dify dify) {
        if (!StringUtils.hasText(dify.getBaseUrl())) {
            log.error("Dify baseUrl is missing");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify 未配置，请补充 app.report.dify.base-url"
            );
        }
        if (!StringUtils.hasText(dify.getApiKey())) {
            log.error("Dify apiKey is missing");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify 未配置，请补充 app.report.dify.api-key"
            );
        }
        if (!StringUtils.hasText(dify.getEndpoint())) {
            log.error("Dify endpoint is missing");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify 未配置，请补充 app.report.dify.endpoint"
            );
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            log.error("Dify response is empty");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dify Workflow 返回为空");
        }

        Object taskId = response.get("task_id");
        Object workflowRunId = response.get("workflow_run_id");
        Object data = response.get("data");

        if (data instanceof Map<?, ?> dataMap) {
            Object status = dataMap.get("status");
            if (status instanceof String workflowStatus && "failed".equalsIgnoreCase(workflowStatus)) {
                Object error = dataMap.get("error");
                log.error(
                        "Dify workflow returned failed status: taskId={}, workflowRunId={}, error={}, dataKeys={}",
                        taskId,
                        workflowRunId,
                        error,
                        dataMap.keySet()
                );
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Dify Workflow 执行失败" + buildTraceSuffix(taskId, workflowRunId) + (error == null ? "" : "，error=" + error)
                );
            }

            Object outputs = dataMap.get("outputs");
            if (outputs instanceof Map<?, ?> outputMap) {
                log.info("Dify workflow outputs keys: {}", outputMap.keySet());
                for (String key : new String[]{"result", "report", "content", "text", "answer"}) {
                    Object value = outputMap.get(key);
                    if (value instanceof String content && StringUtils.hasText(content)) {
                        return content.trim();
                    }
                }
                log.error(
                        "Dify workflow outputs do not contain a readable text field: taskId={}, workflowRunId={}, outputsKeys={}",
                        taskId,
                        workflowRunId,
                        outputMap.keySet()
                );
            } else {
                log.error(
                        "Dify workflow response missing outputs map: taskId={}, workflowRunId={}, dataKeys={}",
                        taskId,
                        workflowRunId,
                        dataMap.keySet()
                );
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "无法从 Dify Workflow 响应中解析报告正文" + buildTraceSuffix(taskId, workflowRunId)
        );
    }

    private String buildMockReport(DifyWorkflowRequest workflowRequest) {
        List<String> titles = extractTitles(workflowRequest.content());
        StringBuilder builder = new StringBuilder();
        builder.append("《").append(workflowRequest.title()).append("》").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("一、核心资讯摘要").append(System.lineSeparator());

        int summaryCount = Math.min(5, titles.size());
        for (int i = 0; i < summaryCount; i++) {
            String title = titles.get(i);
            builder.append("【").append(title).append("】").append(System.lineSeparator());
            builder.append("本段为模拟生成内容，用于联调数据库查询、报告生成与文件导出流程。")
                    .append("后端已按日期 ").append(workflowRequest.newsDate())
                    .append(" 从资讯池查询到原始记录，并将这些记录拼装后送入当前 mock Dify 服务。")
                    .append("因此这段内容不代表真实模型分析结果，而是一个稳定、可重复的测试响应。")
                    .append("在接入真实 Dify Workflow 后，这里会替换为大模型生成的正式日报文本。")
                    .append("当前摘要保留原标题，用于验证报告正文、下载接口、Word/PDF 导出以及 Apifox 调试链路是否全部打通。")
                    .append("来源标签：模拟生成")
                    .append(System.lineSeparator()).append(System.lineSeparator());
        }

        builder.append("二、备选资讯").append(System.lineSeparator());
        for (int i = summaryCount; i < Math.min(summaryCount + 14, titles.size()); i++) {
            builder.append(i - summaryCount + 1)
                    .append(". ")
                    .append(titles.get(i))
                    .append("。理由：该条已进入同日期资讯池，可作为正式接入 Dify Workflow 后的候选资讯。")
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private List<String> extractTitles(String content) {
        List<String> titles = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (line.startsWith("标题：")) {
                String title = line.substring("标题：".length()).trim();
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
        }
        return titles;
    }

    private String buildTraceSuffix(Object taskId, Object workflowRunId) {
        StringBuilder builder = new StringBuilder();
        if (taskId != null) {
            builder.append("，task_id=").append(taskId);
        }
        if (workflowRunId != null) {
            builder.append("，workflow_run_id=").append(workflowRunId);
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

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
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
