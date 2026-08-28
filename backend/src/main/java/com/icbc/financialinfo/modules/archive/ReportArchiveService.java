package com.icbc.financialinfo.modules.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ReportArchiveService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CHINESE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日");
    private static final String REPORT_NAME = "每日经济金融信息";
    private static final String DEPARTMENT_NAME = "数据管理部";
    private static final String COPYRIGHT_NOTICE =
            "注：限于版权方对使用权授权范围的约束，请勿将安邦信息（每日金融、每日经济）对外转发。"
                    + "具体内容可详见“北京资讯管理”邮箱每日下午发送的同名邮件。";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Path archiveRoot;
    private final Path reportFont;
    private final Path reportTitleFont;

    public ReportArchiveService(JdbcTemplate jdbc,
                                ObjectMapper objectMapper,
                                @Value("${app.archive.root:./data/archives}") String archiveRoot,
                                @Value("${app.archive.report-font:}") String reportFont,
                                @Value("${app.archive.report-title-font:}") String reportTitleFont) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.archiveRoot = Path.of(archiveRoot).toAbsolutePath().normalize();
        this.reportFont = reportFont == null || reportFont.isBlank() ? null : Path.of(reportFont).toAbsolutePath();
        this.reportTitleFont = reportTitleFont == null || reportTitleFont.isBlank()
                ? null : Path.of(reportTitleFont).toAbsolutePath();
    }

    public PreparedArtifacts prepare(long reportId) {
        ReportData report = report(reportId);
        List<ArticleData> articles = articles(reportId);
        if (articles.isEmpty()) throw new DepartmentBusinessException(409, "报告没有可发送的资讯条目");
        Path directory = archiveRoot.resolve(report.reportDate()).resolve("report-" + reportId).normalize();
        if (!directory.startsWith(archiveRoot)) throw new DepartmentBusinessException(500, "归档目录配置无效");
        try {
            Files.createDirectories(directory);
            String baseName = safeFileName(report.reportTitle()) + "-" + report.reportDate();
            Path pdf = directory.resolve(baseName + ".pdf");
            Path docx = directory.resolve(baseName + ".docx");
            Path raw = directory.resolve("原始资讯数据.csv");
            Path review = directory.resolve("审核日志.csv");
            writePdf(report, articles, pdf);
            writeDocx(report, articles, docx);
            writeRawCsv(reportId, raw);
            writeReviewCsv(reportId, review);
            return new PreparedArtifacts(report, pdf, docx, raw, review);
        } catch (IOException exception) {
            throw new DepartmentBusinessException(500, "报告文件生成失败：" + conciseMessage(exception));
        }
    }

    public ArchiveResult archive(long reportId, long archivedBy, PreparedArtifacts artifacts) {
        Path directory = artifacts.pdf().getParent();
        Path mailLog = directory.resolve("邮件发送日志.csv");
        Path archivePackage = directory.resolve("报告归档包.zip");
        try {
            writeMailLogCsv(reportId, mailLog);
            zip(archivePackage, List.of(artifacts.pdf(), artifacts.docx(), artifacts.rawCsv(), artifacts.reviewCsv(), mailLog));
            String hash = sha256(archivePackage);
            jdbc.update("""
                    INSERT INTO archive_record(
                        report_id,final_report_path,raw_package_path,review_log_path,mail_log_path,
                        archive_package_path,file_hash,archived_by,archived_at)
                    VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        final_report_path=VALUES(final_report_path),raw_package_path=VALUES(raw_package_path),
                        review_log_path=VALUES(review_log_path),mail_log_path=VALUES(mail_log_path),
                        archive_package_path=VALUES(archive_package_path),file_hash=VALUES(file_hash),
                        archived_by=VALUES(archived_by),archived_at=CURRENT_TIMESTAMP
                    """, reportId, absolute(artifacts.pdf()), absolute(artifacts.rawCsv()),
                    absolute(artifacts.reviewCsv()), absolute(mailLog), absolute(archivePackage), hash, archivedBy);
            return new ArchiveResult(archivePackage.toString(), hash);
        } catch (IOException exception) {
            throw new DepartmentBusinessException(500, "报告归档失败：" + conciseMessage(exception));
        }
    }

    private ReportData report(long reportId) {
        return jdbc.query("""
                SELECT id,DATE_FORMAT(report_date,'%Y-%m-%d') report_date,report_title,status
                  FROM report WHERE id=?
                """, (rs, row) -> new ReportData(
                rs.getLong("id"), rs.getString("report_date"), rs.getString("report_title"), rs.getString("status")), reportId)
                .stream().findFirst().orElseThrow(() -> new DepartmentBusinessException(404, "报告不存在"));
    }

    private List<ArticleData> articles(long reportId) {
        return jdbc.query("""
                SELECT a.id,a.category,a.title,a.summary_content,modified.after_content
                  FROM report_article a
                  LEFT JOIN review_record modified
                    ON modified.id=(
                        SELECT MAX(rr.id)
                          FROM review_record rr
                         WHERE rr.report_id=a.report_id
                           AND rr.article_id=a.id
                           AND rr.action_type='MODIFY'
                    )
                 WHERE a.report_id=? AND a.select_type='selected' ORDER BY a.id
                """, (rs, row) -> {
            ModifiedArticle modified = modifiedArticle(
                    rs.getString("after_content"),
                    rs.getString("title"),
                    rs.getString("summary_content"));
            return new ArticleData(rs.getLong("id"), row + 1, rs.getString("category"),
                    modified.title(), modified.summary());
        }, reportId);
    }

    private ModifiedArticle modifiedArticle(String afterContent, String articleTitle, String articleSummary) {
        if (afterContent == null || afterContent.isBlank()) {
            return new ModifiedArticle(articleTitle, articleSummary);
        }
        try {
            JsonNode snapshot = objectMapper.readTree(afterContent);
            JsonNode title = snapshot.get("title");
            JsonNode summary = snapshot.get("summaryContent");
            return new ModifiedArticle(
                    title == null || title.isNull() ? articleTitle : title.asText(),
                    summary == null || summary.isNull() ? articleSummary : summary.asText());
        } catch (IOException ignored) {
            // 兼容早期直接将修改后正文写入 after_content 的记录。
            return new ModifiedArticle(articleTitle, afterContent);
        }
    }

    private void writeDocx(ReportData report, List<ArticleData> articles, Path output) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); OutputStream stream = Files.newOutputStream(output)) {
            configurePage(document);

            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            title.setSpacingBefore(400);
            title.setSpacingAfter(280);
            run(title, REPORT_NAME, 30, true, "SimHei", "FF0000");

            XWPFTable metadata = document.createTable(1, 3);
            metadata.setWidthType(TableWidthType.PCT);
            metadata.setWidth("100%");
            removeTableBorders(metadata);
            metadataCell(metadata.getRow(0).getCell(0), DEPARTMENT_NAME, ParagraphAlignment.LEFT);
            metadataCell(metadata.getRow(0).getCell(1), issueNumber(report), ParagraphAlignment.CENTER);
            metadataCell(metadata.getRow(0).getCell(2), chineseDate(report.reportDate()), ParagraphAlignment.RIGHT);

            XWPFParagraph divider = document.createParagraph();
            divider.setSpacingAfter(320);
            CTPBdr borders = paragraphProperties(divider).addNewPBdr();
            CTBorder bottom = borders.addNewBottom();
            bottom.setVal(STBorder.DOUBLE);
            bottom.setColor("FF0000");
            bottom.setSz(BigInteger.valueOf(12));

            for (ArticleData article : articles) {
                XWPFParagraph itemTitle = document.createParagraph();
                itemTitle.setAlignment(ParagraphAlignment.CENTER);
                itemTitle.setKeepNext(true);
                itemTitle.setSpacingBefore(160);
                itemTitle.setSpacingAfter(100);
                run(itemTitle, articleHeading(article), 14, true, "SimHei", "000000");

                XWPFParagraph summary = document.createParagraph();
                summary.setAlignment(ParagraphAlignment.BOTH);
                summary.setIndentationFirstLine(480);
                summary.setSpacingBetween(1.5);
                summary.setSpacingAfter(120);
                run(summary, normalizeBody(article.summary()), 12, false, "FangSong", "000000");
            }

            XWPFParagraph separator = document.createParagraph();
            separator.setSpacingBefore(120);
            separator.setSpacingAfter(80);
            run(separator, "----------------------------------------------------------------------------------------------------",
                    10, false, "FangSong", "000000");
            XWPFParagraph notice = document.createParagraph();
            notice.setAlignment(ParagraphAlignment.BOTH);
            notice.setIndentationFirstLine(480);
            notice.setSpacingBetween(1.4);
            run(notice, COPYRIGHT_NOTICE, 11, false, "FangSong", "000000");
            document.write(stream);
        }
    }

    private void writePdf(ReportData report, List<ArticleData> articles, Path output) throws IOException {
        if (reportFont == null || !Files.isRegularFile(reportFont)) {
            throw new IOException("未找到中文字体，请通过 REPORT_FONT_PATH 配置字体文件");
        }
        try (PDDocument document = new PDDocument()) {
            PDFont bodyFont = PDType0Font.load(document, reportFont.toFile());
            PDFont titleFont = reportTitleFont != null && Files.isRegularFile(reportTitleFont)
                    ? PDType0Font.load(document, reportTitleFont.toFile()) : bodyFont;
            try (PdfTextWriter writer = new PdfTextWriter(document, bodyFont, titleFont)) {
                writer.header(REPORT_NAME, DEPARTMENT_NAME, issueNumber(report), chineseDate(report.reportDate()));
                for (ArticleData article : articles) {
                    writer.articleTitle(articleHeading(article));
                    writer.body(normalizeBody(article.summary()));
                }
                writer.footer(COPYRIGHT_NOTICE);
            }
            document.save(output.toFile());
        }
    }

    private void writeRawCsv(long reportId, Path output) throws IOException {
        List<String[]> rows = jdbc.query("""
                SELECT a.news_id,n.daily_seq,n.source_row_id,DATE_FORMAT(n.news_date,'%Y-%m-%d'),
                       COALESCE(n.title,a.title),n.original_content,n.content,n.industry,n.area,n.content_hash
                  FROM report_article a
                  JOIN report r ON r.id=a.report_id
                  LEFT JOIN news_pool n ON n.daily_seq=a.news_id AND n.news_date=r.report_date
                 WHERE a.report_id=? AND a.select_type='selected' ORDER BY a.id
                """, (rs, row) -> new String[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)}, reportId);
        writeCsv(output, new String[]{"资讯ID", "序号", "源数据行ID", "资讯日期", "标题", "原始正文", "清洗正文", "行业", "地区", "内容哈希"}, rows);
    }

    private void writeReviewCsv(long reportId, Path output) throws IOException {
        List<String[]> rows = jdbc.query("""
                SELECT rr.id,rr.action_type,COALESCE(u.username,''),rr.before_content,rr.after_content,
                       rr.reason,rr.comment_text,DATE_FORMAT(rr.created_at,'%Y-%m-%d %H:%i:%s')
                  FROM review_record rr LEFT JOIN sys_user u ON u.id=rr.operator_id
                 WHERE rr.report_id=? ORDER BY rr.id
                """, (rs, row) -> new String[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)}, reportId);
        writeCsv(output, new String[]{"记录ID", "操作类型", "操作人", "修改前", "修改后", "原因", "批注", "操作时间"}, rows);
    }

    private void writeMailLogCsv(long reportId, Path output) throws IOException {
        List<String[]> rows = jdbc.query("""
                SELECT ml.id,mt.subject,ml.recipient_name,ml.recipient_email,ml.mail_status,
                       ml.retry_count,ml.error_message,ml.provider_message_id,
                       DATE_FORMAT(ml.sent_at,'%Y-%m-%d %H:%i:%s')
                  FROM mail_task mt JOIN mail_log ml ON ml.mail_task_id=mt.id
                 WHERE mt.report_id=? ORDER BY ml.id
                """, (rs, row) -> new String[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)}, reportId);
        writeCsv(output, new String[]{"日志ID", "邮件主题", "收件人", "收件邮箱", "状态", "重试次数", "失败原因", "消息ID", "发送时间"}, rows);
    }

    private void writeCsv(Path output, String[] header, List<String[]> rows) throws IOException {
        StringBuilder csv = new StringBuilder("\ufeff");
        appendCsvRow(csv, header);
        for (String[] row : rows) appendCsvRow(csv, row);
        Files.writeString(output, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void appendCsvRow(StringBuilder csv, String[] cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            String value = nullToEmpty(cells[index]).replace("\"", "\"\"");
            csv.append('"').append(value).append('"');
        }
        csv.append("\r\n");
    }

    private void zip(Path output, List<Path> files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setLeft(BigInteger.valueOf(1700));
        margins.setRight(BigInteger.valueOf(1700));
        margins.setTop(BigInteger.valueOf(1250));
        margins.setBottom(BigInteger.valueOf(1250));
    }

    private void removeTableBorders(XWPFTable table) {
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblBorders borders = properties.isSetTblBorders()
                ? properties.getTblBorders() : properties.addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.NIL);
        }
    }

    private void metadataCell(XWPFTableCell cell, String text, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(0);
        run(paragraph, text, 12, false, "FangSong", "000000");
    }

    private CTPPr paragraphProperties(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();
        return ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
    }

    private void run(XWPFParagraph paragraph, String text, int size, boolean bold, String font, String color) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(font);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        run.setText(nullToEmpty(text));
    }

    private String issueNumber(ReportData report) { return "第 " + report.id() + " 期"; }
    private String chineseDate(String value) {
        try { return LocalDate.parse(value).format(CHINESE_DATE_FORMAT); }
        catch (RuntimeException ignored) { return nullToEmpty(value); }
    }
    private String normalizeBody(String value) {
        return nullToEmpty(value).replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
    private String articleHeading(ArticleData article) {
        String title = nullToEmpty(article.title()).trim();
        if (title.startsWith("【") && title.endsWith("】")) return title;
        String label = headlineCategory(article.category());
        if (!label.isBlank() && !title.startsWith(label + "：") && !title.startsWith(label + ":")) {
            title = label + "：" + title;
        }
        return "【" + title + "】";
    }
    private String headlineCategory(String value) {
        if (value == null || value.isBlank()) return "";
        return switch (value.trim().toUpperCase()) {
            case "FINANCE", "MACRO", "金融", "宏观", "宏观经济" -> "形势要点";
            case "MARKET", "市场" -> "市场";
            case "BEIJING_POLICY", "属地政策", "北京" -> "首都经济";
            default -> value.trim();
        };
    }

    private String safeFileName(String value) {
        String safe = nullToEmpty(value).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "每日资讯摘要" : safe;
    }

    private String absolute(Path path) { return path.toAbsolutePath().normalize().toString(); }
    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
    private String nullToEmpty(String value) { return value == null ? "" : value; }

    public record PreparedArtifacts(ReportData report, Path pdf, Path docx, Path rawCsv, Path reviewCsv) {
        public List<AttachmentInfo> attachments() {
            try {
                return List.of(new AttachmentInfo(pdf.getFileName().toString(), Files.size(pdf), "PDF"),
                        new AttachmentInfo(docx.getFileName().toString(), Files.size(docx), "WORD"));
            } catch (IOException exception) {
                throw new DepartmentBusinessException(500, "无法读取报告附件");
            }
        }
    }
    public record AttachmentInfo(String name, long size, String type) {}
    public record ArchiveResult(String packagePath, String fileHash) {}
    public record ReportData(long id, String reportDate, String reportTitle, String status) {}
    private record ModifiedArticle(String title, String summary) {}
    private record ArticleData(long id, int sequence, String category, String title, String summary) {}

    private static final class PdfTextWriter implements AutoCloseable {
        private static final float MARGIN = 68;
        private static final float TOP = 774;
        private static final float BOTTOM = 58;
        private static final float BODY_SIZE = 11.5f;
        private static final float BODY_LINE_HEIGHT = 20.5f;
        private final PDDocument document;
        private final PDFont font;
        private final PDFont titleFont;
        private PDPageContentStream content;
        private float y;

        private PdfTextWriter(PDDocument document, PDFont font, PDFont titleFont) throws IOException {
            this.document = document;
            this.font = font;
            this.titleFont = titleFont;
            newPage();
        }

        private void header(String title, String department, String issue, String date) throws IOException {
            center(title, 30, true, 255, 0, 0);
            y -= 42;
            writeLine(department, MARGIN, 12, false, 0, 0, 0, 0);
            writeCenteredLine(issue, 12, false, 0, 0, 0);
            float dateWidth = textWidth(date, 12);
            writeLine(date, PDRectangle.A4.getWidth() - MARGIN - dateWidth, 12, false, 0, 0, 0, 0);
            y -= 15;
            content.setStrokingColor(255, 0, 0);
            content.setLineWidth(1.2f);
            content.moveTo(MARGIN, y);
            content.lineTo(PDRectangle.A4.getWidth() - MARGIN, y);
            content.stroke();
            content.moveTo(MARGIN, y - 4);
            content.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 4);
            content.stroke();
            content.setStrokingColor(0, 0, 0);
            y -= 38;
        }

        private void articleTitle(String text) throws IOException {
            List<String> lines = wrap(text, 13.5f, PDRectangle.A4.getWidth() - MARGIN * 2 - 24);
            ensureSpace(lines.size() * 21 + BODY_LINE_HEIGHT * 2 + 12);
            for (String line : lines) {
                writeCenteredLine(line, 13.5f, true, 0, 0, 0);
                y -= 21;
            }
            y -= 7;
        }

        private void body(String text) throws IOException {
            float maxWidth = PDRectangle.A4.getWidth() - MARGIN * 2;
            float firstIndent = BODY_SIZE * 2;
            List<TextLine> lines = wrapParagraph(text, BODY_SIZE, maxWidth, firstIndent);
            for (int index = 0; index < lines.size(); index++) {
                TextLine line = lines.get(index);
                ensureSpace(BODY_LINE_HEIGHT);
                float indent = line.first() ? firstIndent : 0;
                float available = maxWidth - indent;
                boolean justify = index < lines.size() - 1 && line.text().codePointCount(0, line.text().length()) > 1;
                writeLine(line.text(), MARGIN + indent, BODY_SIZE, false, 0, 0, 0,
                        justify ? Math.max(0, (available - textWidth(line.text(), BODY_SIZE))
                                / (line.text().codePointCount(0, line.text().length()) - 1)) : 0);
                y -= BODY_LINE_HEIGHT;
            }
            y -= 11;
        }

        private void footer(String notice) throws IOException {
            ensureSpace(74);
            content.setLineDashPattern(new float[]{4, 2}, 0);
            content.setLineWidth(0.8f);
            content.moveTo(MARGIN, y);
            content.lineTo(PDRectangle.A4.getWidth() - MARGIN, y);
            content.stroke();
            content.setLineDashPattern(new float[]{}, 0);
            y -= 22;
            body(notice);
        }

        private void center(String text, float size, boolean bold, int red, int green, int blue) throws IOException {
            writeCenteredLine(text, size, bold, red, green, blue);
        }

        private void writeCenteredLine(String text, float size, boolean bold,
                                       int red, int green, int blue) throws IOException {
            float width = textWidth(text, size, bold);
            writeLine(text, Math.max(MARGIN, (PDRectangle.A4.getWidth() - width) / 2),
                    size, bold, red, green, blue, 0);
        }

        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (!line.isEmpty() && titleFont.getStringWidth(candidate) / 1000 * size > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty() || lines.isEmpty()) lines.add(line.toString());
            return lines;
        }

        private List<TextLine> wrapParagraph(String text, float size, float maxWidth, float firstIndent)
                throws IOException {
            String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ')
                    .replaceAll("\\s+", " ").trim();
            List<TextLine> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            boolean first = true;
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                float available = maxWidth - (first ? firstIndent : 0);
                if (!line.isEmpty() && textWidth(line + character, size) > available) {
                    lines.add(new TextLine(line.toString(), first));
                    line.setLength(0);
                    first = false;
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty() || lines.isEmpty()) lines.add(new TextLine(line.toString(), first));
            return lines;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = TOP;
        }

        private float textWidth(String text, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        private float textWidth(String text, float size, boolean title) throws IOException {
            return (title ? titleFont : font).getStringWidth(text) / 1000 * size;
        }

        private void writeLine(String text, float x, float size, boolean bold,
                               int red, int green, int blue, float characterSpacing) throws IOException {
            content.beginText();
            content.setFont(bold ? titleFont : font, size);
            content.setNonStrokingColor(red, green, blue);
            content.setCharacterSpacing(characterSpacing);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
            content.setCharacterSpacing(0);
            content.setNonStrokingColor(0, 0, 0);
        }

        @Override public void close() throws IOException { if (content != null) content.close(); }
        private record TextLine(String text, boolean first) {}
    }
}
