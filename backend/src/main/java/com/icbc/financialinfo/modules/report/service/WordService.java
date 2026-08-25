package com.icbc.financialinfo.modules.report.service;

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

@Service
public class WordService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger log = LoggerFactory.getLogger(WordService.class);

    public Path writeDailySummary(Path reportDirectory, String reportId, String reportTitle, LocalDate reportDate, String content) {
        Path outputPath = reportDirectory.resolve(reportId + ".docx");
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
        paragraph.setSpacingAfter(300);
        XWPFRun run = paragraph.createRun();
        run.setText(reportTitle);
        run.setBold(true);
        run.setFontSize(16);
        run.setFontFamily("Microsoft YaHei");
    }

    private void writeMeta(XWPFDocument document, LocalDate reportDate) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(200);
        XWPFRun run = paragraph.createRun();
        run.setText("生成日期：" + reportDate.format(DATE_FORMATTER));
        run.setFontSize(10);
        run.setColor("666666");
        run.setFontFamily("Microsoft YaHei");
    }

    private void writeBody(XWPFDocument document, String content) {
        String normalized = content.replace("```", "").replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\\R\\s*\\R");
        for (String block : paragraphs) {
            if (block.isBlank()) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.BOTH);
            paragraph.setSpacingAfter(160);
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontSize(isHeading(block) ? 13 : 11);
            run.setBold(isHeading(block));
            run.setText(block.trim());
        }
    }

    private boolean isHeading(String block) {
        String text = block.trim();
        return text.startsWith("一、") || text.startsWith("二、") || text.startsWith("三、")
                || (text.startsWith("《") && text.endsWith("》"));
    }
}
