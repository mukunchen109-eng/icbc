package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.service.ReportTextFormatter.Block;
import com.icbc.financialinfo.modules.report.service.ReportTextFormatter.BlockType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WordService {

    private static final String FILE_NAME = "每日资讯摘要";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger log = LoggerFactory.getLogger(WordService.class);

    public Path writeDailySummary(Path reportDirectory, String fileBaseName, String reportTitle, LocalDate reportDate, String content) {
        String resolvedFileName = fileBaseName == null || fileBaseName.isBlank() ? FILE_NAME : fileBaseName.trim();
        Path outputPath = reportDirectory.resolve(resolvedFileName + ".docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream outputStream = Files.newOutputStream(outputPath)) {
            writeTitle(document, reportTitle);
            writeMeta(document, reportDate);
            writeBody(document, content);
            document.write(outputStream);
            return outputPath;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入 WORD 报告失败", ex);
        }
    }

    private void writeTitle(XWPFDocument document, String reportTitle) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(240);
        XWPFRun run = paragraph.createRun();
        run.setText(reportTitle);
        run.setBold(true);
        run.setFontSize(16);
        run.setFontFamily("Microsoft YaHei");
    }

    private void writeMeta(XWPFDocument document, LocalDate reportDate) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(180);
        XWPFRun run = paragraph.createRun();
        run.setText("生成日期：" + reportDate.format(DATE_FORMATTER));
        run.setFontSize(10);
        run.setColor("666666");
        run.setFontFamily("Microsoft YaHei");
    }

    private void writeBody(XWPFDocument document, String content) {
        List<Block> blocks = ReportTextFormatter.format(content);
        for (Block block : blocks) {
            switch (block.type()) {
                case BLANK -> addBlankParagraph(document, 120);
                case HEADING -> addParagraph(document, block.text(), 13, true, 220, 120, ParagraphAlignment.LEFT, false, null);
                case SUBHEADING -> addParagraph(document, block.text(), 12, true, 160, 80, ParagraphAlignment.LEFT, false, null);
                case BULLET -> addParagraph(document, block.text(), 11, false, 240, 60, ParagraphAlignment.LEFT, true, null);
                case QUOTE -> addParagraph(document, block.text(), 11, false, 240, 80, ParagraphAlignment.LEFT, false, null);
                case META -> addParagraph(document, block.text(), 10, false, 0, 80, ParagraphAlignment.LEFT, false, "666666");
                case BODY -> addParagraph(document, block.text(), 11, false, 0, 90, ParagraphAlignment.BOTH, false, null);
            }
        }
    }

    private void addBlankParagraph(XWPFDocument document, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
    }

    private void addParagraph(
            XWPFDocument document,
            String text,
            int fontSize,
            boolean bold,
            int indentationLeft,
            int spacingAfter,
            ParagraphAlignment alignment,
            boolean bullet,
            String color
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(spacingAfter);
        if (indentationLeft > 0) {
            paragraph.setIndentationLeft(indentationLeft);
        }
        if (bullet) {
            paragraph.setIndentationHanging(180);
        }

        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(fontSize);
        run.setBold(bold);
        if (color != null) {
            run.setColor(color);
        }
        if (bullet) {
            run.setText("• " + text);
        } else {
            run.setText(text);
        }
    }
}
