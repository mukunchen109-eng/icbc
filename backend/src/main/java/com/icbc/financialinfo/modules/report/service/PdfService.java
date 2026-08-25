package com.icbc.financialinfo.modules.report.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final float PAGE_MARGIN = 48F;
    private static final float BODY_FONT_SIZE = 11F;
    private static final float TITLE_FONT_SIZE = 15F;
    private static final float META_FONT_SIZE = 9.5F;
    private static final float LEADING = 17F;

    public Path writeDailySummary(Path reportDirectory, String reportId, String reportTitle, LocalDate reportDate, String content) {
        Path outputPath = reportDirectory.resolve(reportId + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadChineseFont(document);
            List<LineSpec> lines = buildLines(reportTitle, reportDate, content, font);
            writeLines(document, lines);
            document.save(outputPath.toFile());
            return outputPath;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入 PDF 报告失败", ex);
        }
    }

    private void writeLines(PDDocument document, List<LineSpec> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float y = page.getMediaBox().getHeight() - PAGE_MARGIN;
        PDPageContentStream stream = new PDPageContentStream(document, page);

        for (LineSpec line : lines) {
            if (y <= PAGE_MARGIN) {
                stream.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = page.getMediaBox().getHeight() - PAGE_MARGIN;
            }

            if (!line.text().isEmpty()) {
                stream.beginText();
                stream.setFont(line.font(), line.fontSize());
                stream.newLineAtOffset(PAGE_MARGIN, y);
                stream.showText(line.text());
                stream.endText();
            }
            y -= line.leading();
        }

        stream.close();
    }

    private List<LineSpec> buildLines(String reportTitle, LocalDate reportDate, String content, PDFont font) throws IOException {
        float width = PDRectangle.A4.getWidth() - PAGE_MARGIN * 2;
        List<LineSpec> result = new ArrayList<>();
        for (String line : wrapText(reportTitle, font, TITLE_FONT_SIZE, width)) {
            result.add(new LineSpec(line, font, TITLE_FONT_SIZE, LEADING));
        }
        result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F));
        result.add(new LineSpec("生成日期：" + reportDate.format(DATE_FORMATTER), font, META_FONT_SIZE, LEADING));
        result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F));

        String normalizedContent = content.replace("```", "").replace("\r\n", "\n").trim();
        for (String paragraph : normalizedContent.split("\\n\\s*\\n")) {
            if (paragraph.isBlank()) {
                continue;
            }
            float fontSize = isHeading(paragraph) ? 12.5F : BODY_FONT_SIZE;
            for (String line : wrapText(paragraph.trim(), font, fontSize, width)) {
                result.add(new LineSpec(line, font, fontSize, LEADING));
            }
            result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F));
        }
        return result;
    }

    private boolean isHeading(String text) {
        String normalized = text.trim();
        return normalized.startsWith("一、") || normalized.startsWith("二、") || normalized.startsWith("三、")
                || (normalized.startsWith("《") && normalized.endsWith("》"));
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String rawLine : text.split("\\n")) {
            if (rawLine.isBlank()) {
                lines.add("");
                continue;
            }
            currentLine.setLength(0);
            for (int i = 0; i < rawLine.length(); i++) {
                char currentChar = rawLine.charAt(i);
                String candidate = currentLine + String.valueOf(currentChar);
                float candidateWidth = font.getStringWidth(candidate) / 1000 * fontSize;
                if (candidateWidth > maxWidth && currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                currentLine.append(currentChar);
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        for (String candidate : List.of(
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\simsun.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/System/Library/Fonts/PingFang.ttc"
        )) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                try (InputStream inputStream = Files.newInputStream(path)) {
                    return PDType0Font.load(document, inputStream);
                }
            }
        }
        throw new IOException("未找到可用的中文字体文件，无法生成 PDF");
    }

    private record LineSpec(String text, PDFont font, float fontSize, float leading) {
    }
}
