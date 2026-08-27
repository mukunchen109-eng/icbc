package com.icbc.financialinfo.modules.archive;

import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
    private final JdbcTemplate jdbc;
    private final Path archiveRoot;
    private final Path reportFont;

    public ReportArchiveService(JdbcTemplate jdbc,
                                @Value("${app.archive.root:./data/archives}") String archiveRoot,
                                @Value("${app.archive.report-font:}") String reportFont) {
        this.jdbc = jdbc;
        this.archiveRoot = Path.of(archiveRoot).toAbsolutePath().normalize();
        this.reportFont = reportFont == null || reportFont.isBlank() ? null : Path.of(reportFont).toAbsolutePath();
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
                SELECT id,category,title,summary_content
                  FROM report_article WHERE report_id=? ORDER BY id
                """, (rs, row) -> new ArticleData(
                rs.getLong("id"), row + 1, rs.getString("category"),
                rs.getString("title"), rs.getString("summary_content")), reportId);
    }

    private void writeDocx(ReportData report, List<ArticleData> articles, Path output) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); OutputStream stream = Files.newOutputStream(output)) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            run(title, report.reportTitle(), 18, true);
            XWPFParagraph date = document.createParagraph();
            date.setAlignment(ParagraphAlignment.CENTER);
            run(date, "报告日期：" + report.reportDate(), 11, false);
            String category = null;
            int categoryIndex = 0;
            for (ArticleData article : articles) {
                if (!equals(category, article.category())) {
                    category = article.category();
                    XWPFParagraph heading = document.createParagraph();
                    run(heading, chineseNumber(++categoryIndex) + "、" + displayCategory(category), 14, true);
                }
                XWPFParagraph itemTitle = document.createParagraph();
                run(itemTitle, article.sequence() + ".【" + article.title() + "】", 12, true);
                XWPFParagraph summary = document.createParagraph();
                summary.setIndentationFirstLine(420);
                run(summary, nullToEmpty(article.summary()), 11, false);
            }
            document.write(stream);
        }
    }

    private void writePdf(ReportData report, List<ArticleData> articles, Path output) throws IOException {
        if (reportFont == null || !Files.isRegularFile(reportFont)) {
            throw new IOException("未找到中文字体，请通过 REPORT_FONT_PATH 配置字体文件");
        }
        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType0Font.load(document, reportFont.toFile());
            try (PdfTextWriter writer = new PdfTextWriter(document, font)) {
                writer.center(report.reportTitle(), 18, 28);
                writer.center("报告日期：" + report.reportDate(), 11, 24);
                String category = null;
                int categoryIndex = 0;
                for (ArticleData article : articles) {
                    if (!equals(category, article.category())) {
                        category = article.category();
                        writer.paragraph(chineseNumber(++categoryIndex) + "、" + displayCategory(category), 14, 22);
                    }
                    writer.paragraph(article.sequence() + ".【" + article.title() + "】", 12, 19);
                    writer.paragraph(nullToEmpty(article.summary()), 11, 18);
                }
            }
            document.save(output.toFile());
        }
    }

    private void writeRawCsv(long reportId, Path output) throws IOException {
        List<String[]> rows = jdbc.query("""
                SELECT a.news_id,n.daily_seq,n.source_row_id,DATE_FORMAT(n.news_date,'%Y-%m-%d'),
                       COALESCE(n.title,a.title),n.original_content,n.content,n.industry,n.area,n.content_hash
                  FROM report_article a LEFT JOIN news_pool n ON n.id=a.news_id
                 WHERE a.report_id=? ORDER BY a.id
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

    private void run(XWPFParagraph paragraph, String text, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(nullToEmpty(text));
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
    private boolean equals(String left, String right) { return java.util.Objects.equals(left, right); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String displayCategory(String value) {
        if (value == null || value.isBlank()) return "资讯";
        return switch (value.toUpperCase()) {
            case "FINANCE" -> "金融领域";
            case "MACRO" -> "宏观经济";
            case "BEIJING_POLICY" -> "属地政策";
            default -> value;
        };
    }
    private String chineseNumber(int number) {
        String[] values = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        return number >= 1 && number <= 10 ? values[number] : String.valueOf(number);
    }

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
    private record ArticleData(long id, int sequence, String category, String title, String summary) {}

    private static final class PdfTextWriter implements AutoCloseable {
        private static final float MARGIN = 52;
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream content;
        private float y;

        private PdfTextWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void center(String text, float size, float gap) throws IOException {
            ensureSpace(gap);
            float width = font.getStringWidth(text) / 1000 * size;
            writeLine(text, Math.max(MARGIN, (PDRectangle.A4.getWidth() - width) / 2), size);
            y -= gap;
        }

        private void paragraph(String text, float size, float lineHeight) throws IOException {
            List<String> lines = wrap(text, size, PDRectangle.A4.getWidth() - MARGIN * 2);
            for (String line : lines) {
                ensureSpace(lineHeight);
                writeLine(line, MARGIN, size);
                y -= lineHeight;
            }
            y -= 5;
        }

        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty() || lines.isEmpty()) lines.add(line.toString());
            return lines;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private void writeLine(String text, float x, float size) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
        }

        @Override public void close() throws IOException { if (content != null) content.close(); }
    }
}
