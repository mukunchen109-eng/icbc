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
            JsonNode selected = locateSelectedNode(root);
            if (selected == null || !selected.isArray()) {
                return List.of();
            }

            List<DifySummaryArticle> result = new ArrayList<>();
            for (JsonNode item : selected) {
                Long newsId = requireLong(item, "id");
                String category = requireText(item, "category");
                String title = requireText(item, "title");
                String summary = requireText(item, "summary");
                String sourceLabel = optionalText(item, "sourceLabel", "source_label");
                result.add(new DifySummaryArticle(newsId, category, title, summary, sourceLabel));
            }
            return List.copyOf(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Dify selected articles", ex);
        }
    }

    private static JsonNode locateSelectedNode(JsonNode node) {
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
                    return locateSelectedNode(OBJECT_MAPPER.readTree(text));
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
        if (selected != null && selected.isArray()) {
            return selected;
        }

        JsonNode result = node.get("result");
        if (result != null) {
            JsonNode nested = locateSelectedNode(result);
            if (nested != null) {
                return nested;
            }
        }

        JsonNode outputs = node.path("data").path("outputs").path("result");
        if (!outputs.isMissingNode()) {
            return locateSelectedNode(outputs);
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
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
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
