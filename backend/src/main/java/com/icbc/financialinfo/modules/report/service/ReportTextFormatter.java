package com.icbc.financialinfo.modules.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ReportTextFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+");
    private static final Pattern BULLET_LINE = Pattern.compile("^[\\-\\*•·]\\s+.+");
    private static final Pattern QUOTE_LINE = Pattern.compile("^>\\s*.+");
    private static final Pattern META_LINE = Pattern.compile("^(生成日期|发布日期|报告日期|日期|时间)[:：].+");
    private static final Pattern CHINESE_HEADING = Pattern.compile(
            "^(第[一二三四五六七八九十0-9]+[章节部分篇节]?|[一二三四五六七八九十]+[、\\.．].+)"
    );
    private static final Pattern NUMERIC_HEADING = Pattern.compile("^[0-9]+[、\\.．\\)]\\s*.+");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[一二三四五六七八九十0-9]+|（[一二三四五六七八九十0-9]+）)[、\\.．\\)]\\s*.+");

    private ReportTextFormatter() {
    }

    public enum BlockType {
        HEADING,
        SUBHEADING,
        BULLET,
        QUOTE,
        META,
        BODY,
        BLANK
    }

    public record Block(BlockType type, String text) {
    }

    public static List<Block> format(String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<Block> parsedJson = tryFormatJson(normalized);
        if (parsedJson != null) {
            return parsedJson;
        }

        return formatPlainText(normalized);
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

    private static List<Block> tryFormatJson(String content) {
        if (content.isBlank()) {
            return null;
        }
        char first = content.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            List<Block> blocks = new ArrayList<>();
            if (appendStructuredBlocks(root, blocks)) {
                return compressBlankBlocks(blocks);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static boolean appendStructuredBlocks(JsonNode node, List<Block> blocks) {
        if (node == null || node.isNull()) {
            return false;
        }

        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return false;
            }
            if (looksLikeJson(text)) {
                try {
                    return appendStructuredBlocks(OBJECT_MAPPER.readTree(text), blocks);
                } catch (Exception ignored) {
                    return false;
                }
            }
            addTextBlocks(blocks, text);
            return true;
        }

        if (node.isArray()) {
            boolean appended = false;
            for (JsonNode item : node) {
                appended |= appendStructuredBlocks(item, blocks);
            }
            return appended;
        }

        if (!node.isObject()) {
            return false;
        }

        if (appendResultField(node, blocks)) {
            return true;
        }

        boolean hasStructuredSection = false;
        hasStructuredSection |= appendSectionFromArray(node.get("selected"), "精选内容", blocks, false);
        hasStructuredSection |= appendSectionFromArray(node.get("alternatives"), "备选内容", blocks, true);
        hasStructuredSection |= appendSectionFromArray(node.get("articles"), "正文内容", blocks, false);
        hasStructuredSection |= appendSectionFromArray(node.get("items"), "条目", blocks, false);

        if (hasStructuredSection) {
            return true;
        }

        String title = firstText(node, "title", "name", "headline", "subject");
        String summary = firstText(node, "summary", "content", "text", "body", "result", "answer");
        if (title != null || summary != null) {
            if (title != null) {
                blocks.add(new Block(BlockType.HEADING, title));
            }
            if (summary != null) {
                addTextBlocks(blocks, summary);
            }
            return true;
        }

        return false;
    }

    private static boolean appendResultField(JsonNode node, List<Block> blocks) {
        JsonNode result = node.get("result");
        if (result == null || result.isNull()) {
            return false;
        }
        return appendStructuredBlocks(result, blocks);
    }

    private static boolean appendSectionFromArray(JsonNode node, String sectionTitle, List<Block> blocks, boolean includeReason) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return false;
        }

        blocks.add(new Block(BlockType.HEADING, sectionTitle + "（" + node.size() + "条）"));
        int index = 1;
        for (JsonNode item : node) {
            String title = firstText(item, "title", "name", "headline", "subject");
            String summary = firstText(item, "summary", "content", "text", "body", "result", "answer");
            String reason = firstText(item, "reason", "why", "explanation", "note");

            String headingText = title != null ? title : "第" + index + "条";
            blocks.add(new Block(BlockType.SUBHEADING, index + ". " + headingText));

            if (includeReason) {
                if (reason != null) {
                    addTextBlocks(blocks, "推荐理由：" + reason);
                } else if (summary != null) {
                    addTextBlocks(blocks, summary);
                }
            } else {
                if (summary != null) {
                    addTextBlocks(blocks, summary);
                } else if (reason != null) {
                    addTextBlocks(blocks, reason);
                }
            }

            blocks.add(new Block(BlockType.BLANK, ""));
            index++;
        }
        return true;
    }

    private static List<Block> formatPlainText(String normalized) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = normalized.split("\\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                addBlank(blocks);
                continue;
            }

            blocks.add(classifyLine(line));
        }
        return compressBlankBlocks(blocks);
    }

    private static Block classifyLine(String line) {
        if (MARKDOWN_HEADING.matcher(line).matches()) {
            return new Block(BlockType.HEADING, stripMarkdownHeading(line));
        }
        if (isHeading(line)) {
            return new Block(BlockType.HEADING, line);
        }
        if (META_LINE.matcher(line).matches()) {
            return new Block(BlockType.META, line);
        }
        if (BULLET_LINE.matcher(line).matches()) {
            return new Block(BlockType.BULLET, stripBulletPrefix(line));
        }
        if (QUOTE_LINE.matcher(line).matches()) {
            return new Block(BlockType.QUOTE, line.substring(1).strip());
        }
        if (looksLikeSubheading(line)) {
            return new Block(BlockType.SUBHEADING, line);
        }
        return new Block(BlockType.BODY, line);
    }

    private static boolean isHeading(String line) {
        return CHINESE_HEADING.matcher(line).matches() || NUMERIC_HEADING.matcher(line).matches();
    }

    private static boolean looksLikeSubheading(String line) {
        return LIST_MARKER.matcher(line).matches();
    }

    private static boolean looksLikeJson(String text) {
        return !text.isEmpty() && (text.charAt(0) == '{' || text.charAt(0) == '[');
    }

    private static String stripMarkdownHeading(String line) {
        return line.replaceFirst("^#{1,6}\\s*", "").strip();
    }

    private static String stripBulletPrefix(String line) {
        return line.replaceFirst("^[\\-\\*•·]\\s*", "").strip();
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static void addTextBlocks(List<Block> blocks, String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return;
        }

        for (String line : normalized.split("\\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                addBlank(blocks);
                continue;
            }
            blocks.add(classifyLine(trimmed));
        }
    }

    private static void addBlank(List<Block> blocks) {
        if (blocks.isEmpty() || blocks.get(blocks.size() - 1).type() != BlockType.BLANK) {
            blocks.add(new Block(BlockType.BLANK, ""));
        }
    }

    private static List<Block> compressBlankBlocks(List<Block> blocks) {
        List<Block> result = new ArrayList<>();
        for (Block block : blocks) {
            if (block.type() == BlockType.BLANK && !result.isEmpty() && result.get(result.size() - 1).type() == BlockType.BLANK) {
                continue;
            }
            result.add(block);
        }

        while (!result.isEmpty() && result.get(0).type() == BlockType.BLANK) {
            result.remove(0);
        }
        while (!result.isEmpty() && result.get(result.size() - 1).type() == BlockType.BLANK) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
