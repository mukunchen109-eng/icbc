package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.service.ReportTextFormatter.Block;
import com.icbc.financialinfo.modules.report.service.ReportTextFormatter.BlockType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
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

    private static final String FILE_NAME = "每日资讯摘要";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final float PAGE_MARGIN = 48F;
    private static final float BODY_FONT_SIZE = 11F;
    private static final float HEADING_FONT_SIZE = 14F;
    private static final float SUBHEADING_FONT_SIZE = 12F;
    private static final float META_FONT_SIZE = 9.5F;
    private static final float LEADING = 17F;
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    public Path writeDailySummary(Path reportDirectory, String fileBaseName, String reportTitle, LocalDate reportDate, String content) {
        String resolvedFileName = fileBaseName == null || fileBaseName.isBlank() ? FILE_NAME : fileBaseName.trim();
        Path outputPath = reportDirectory.resolve(resolvedFileName + ".pdf");
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

        PDPageContentStream currentStream = new PDPageContentStream(document, page);
        try {
            for (LineSpec line : lines) {
                if (y <= PAGE_MARGIN) {
                    currentStream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    currentStream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - PAGE_MARGIN;
                }

                if (!line.text().isEmpty()) {
                    currentStream.beginText();
                    currentStream.setRenderingMode(RenderingMode.FILL);
                    currentStream.setFont(line.font(), line.fontSize());
                    if (line.color() != null) {
                        currentStream.setNonStrokingColor(line.color());
                    } else {
                        currentStream.setNonStrokingColor(Color.BLACK);
                    }
                    currentStream.newLineAtOffset(PAGE_MARGIN + line.indent(), y);
                    currentStream.showText(line.text());
                    currentStream.endText();
                }
                y -= line.leading();
            }
        } finally {
            currentStream.close();
        }
    }

    private List<LineSpec> buildLines(String reportTitle, LocalDate reportDate, String content, PDFont font) throws IOException {
        float width = PDRectangle.A4.getWidth() - PAGE_MARGIN * 2;
        List<LineSpec> result = new ArrayList<>();
        for (String line : wrapText(reportTitle, font, HEADING_FONT_SIZE, width, 0)) {
            result.add(new LineSpec(line, font, HEADING_FONT_SIZE, LEADING, 0, Color.BLACK));
        }
        result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F, 0, Color.BLACK));
        result.add(new LineSpec("生成日期：" + reportDate.format(DATE_FORMATTER), font, META_FONT_SIZE, LEADING, 0, new Color(102, 102, 102)));
        result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F, 0, Color.BLACK));

        for (Block block : ReportTextFormatter.format(content)) {
            switch (block.type()) {
                case BLANK -> result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F, 0, Color.BLACK));
                case HEADING -> addWrappedBlock(result, block.text(), font, HEADING_FONT_SIZE, LEADING + 1F, width, 0, Color.BLACK);
                case SUBHEADING -> addWrappedBlock(result, block.text(), font, SUBHEADING_FONT_SIZE, LEADING, width, 0, Color.BLACK);
                case BULLET -> addWrappedBlock(result, "• " + block.text(), font, BODY_FONT_SIZE, LEADING, width, 14F, Color.BLACK);
                case QUOTE -> addWrappedBlock(result, block.text(), font, BODY_FONT_SIZE, LEADING, width, 14F, new Color(70, 70, 70));
                case META -> addWrappedBlock(result, block.text(), font, META_FONT_SIZE, LEADING, width, 0, new Color(102, 102, 102));
                case BODY -> addWrappedBlock(result, block.text(), font, BODY_FONT_SIZE, LEADING, width, 0, Color.BLACK);
            }
        }
        return result;
    }

    private void addWrappedBlock(
            List<LineSpec> result,
            String text,
            PDFont font,
            float fontSize,
            float leading,
            float width,
            float indent,
            Color color
    ) throws IOException {
        for (String line : wrapText(text, font, fontSize, width, indent)) {
            result.add(new LineSpec(line, font, fontSize, leading, indent, color));
        }
        result.add(new LineSpec("", font, BODY_FONT_SIZE, LEADING * 0.6F, 0, Color.BLACK));
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth, float indent) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        float availableWidth = Math.max(1F, maxWidth - indent);

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
                if (candidateWidth > availableWidth && currentLine.length() > 0) {
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

    private record LineSpec(String text, PDFont font, float fontSize, float leading, float indent, Color color) {
    }
}
