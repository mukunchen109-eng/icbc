package com.icbc.financialinfo.modules.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.financialinfo.modules.report.model.DifySummaryArticle;

import java.util.ArrayList;
import java.util.List;

public final class DifySummaryArticleParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DifySummaryArticleParser() {
    }

    public static List<DifySummaryArticle> parseSelectedArticles(String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(normalized);
            JsonNode selectionContainer = locateSelectionContainer(root);
            if (selectionContainer == null) {
                return List.of();
            }

            List<DifySummaryArticle> result = new ArrayList<>();
            if (selectionContainer.isArray()) {
                appendArticles(result, selectionContainer, "selected");
            } else {
                appendArticles(result, selectionContainer.get("selected"), "selected");
                appendArticles(result, selectionContainer.get("alternatives"), "alternatives");
            }
            return List.copyOf(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Dify summary articles", ex);
        }
    }

    private static void appendArticles(List<DifySummaryArticle> result, JsonNode articles, String selectType) {
        if (articles == null || !articles.isArray()) {
            return;
        }
        for (JsonNode item : articles) {
            Long newsId = requireLong(item, "id");
            String category = requireText(item, "category");
            String title = requireText(item, "title");
            String summary = requireText(item, "summary");
            String sourceLabel = optionalText(item, "sourceLabel", "source_label");
            String reason = optionalText(item, "reason");
            result.add(new DifySummaryArticle(newsId, category, title, summary, sourceLabel, reason, selectType));
        }
    }

    private static JsonNode locateSelectionContainer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            if (looksLikeJson(text)) {
                try {
                    return locateSelectionContainer(OBJECT_MAPPER.readTree(text));
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }

        JsonNode selected = node.get("selected");
        JsonNode alternatives = node.get("alternatives");
        if ((selected != null && selected.isArray()) || (alternatives != null && alternatives.isArray())) {
            return node;
        }

        JsonNode result = node.get("result");
        if (result != null) {
            JsonNode nested = locateSelectionContainer(result);
            if (nested != null) {
                return nested;
            }
        }

        JsonNode outputs = node.path("data").path("outputs").path("result");
        if (!outputs.isMissingNode()) {
            return locateSelectionContainer(outputs);
        }
        return null;
    }

    private static String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace("```", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static boolean looksLikeJson(String value) {
        return !value.isEmpty() && (value.charAt(0) == '{' || value.charAt(0) == '[');
    }

    private static String requireText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("Missing required field: " + fieldName);
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static Long requireLong(JsonNode node, String fieldName) {
        String text = requireText(node, fieldName);
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid numeric field: " + fieldName + "=" + text, ex);
        }
    }
}
