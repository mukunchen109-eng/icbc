package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.model.DifyWorkflowRequest;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.NewsPoolRecord;
import com.icbc.financialinfo.modules.report.model.ReportFileDescriptor;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import com.icbc.financialinfo.modules.report.repository.NewsPoolRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportService {

    private static final int MIN_REQUIRED_ARTICLES = 1;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DifyService difyService;
    private final WordService wordService;
    private final PdfService pdfService;
    private final ReportProperties reportProperties;
    private final NewsPoolRepository newsPoolRepository;
    private final Map<String, StoredReport> reports = new ConcurrentHashMap<>();

    public ReportService(
            DifyService difyService,
            WordService wordService,
            PdfService pdfService,
            ReportProperties reportProperties,
            NewsPoolRepository newsPoolRepository
    ) {
        this.difyService = difyService;
        this.wordService = wordService;
        this.pdfService = pdfService;
        this.reportProperties = reportProperties;
        this.newsPoolRepository = newsPoolRepository;
    }

    public List<ReportListItem> listReports() {
        return reports.values().stream()
                .sorted(Comparator.comparing(StoredReport::generatedAt).reversed())
                .map(this::toListItem)
                .toList();
    }

    public GeneratedReportResponse getReport(String reportId) {
        return toResponse(getStoredReport(reportId));
    }

    public GeneratedReportResponse generateDailySummary(GenerateDailySummaryRequest request) {
        LocalDate reportDate = request.reportDate();
        String newsDate = reportDate.format(DATE_FORMATTER);
        String reportTitle = request.reportTitle() == null || request.reportTitle().isBlank()
                ? "每日资讯摘要（" + newsDate + "）"
                : request.reportTitle().trim();

        List<NewsPoolRecord> records = newsPoolRepository.findByNewsDate(newsDate);
        validateRecords(newsDate, records);

        DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(
                newsDate,
                reportTitle,
                buildContent(newsDate, records)
        );
        String generatedContent = difyService.generateDailySummary(workflowRequest);

        String reportId = UUID.randomUUID().toString().replace("-", "");
        Path reportDirectory = prepareReportDirectory(reportId);
        Path wordPath = wordService.writeDailySummary(reportDirectory, reportId, reportTitle, reportDate, generatedContent);
        Path pdfPath = pdfService.writeDailySummary(reportDirectory, reportId, reportTitle, reportDate, generatedContent);

        StoredReport report = new StoredReport(
                reportId,
                reportTitle,
                reportDate,
                generatedContent,
                records.size(),
                Instant.now(),
                wordPath,
                pdfPath
        );
        reports.put(reportId, report);
        return toResponse(report);
    }

    public Path resolveReportFile(String reportId, String format) {
        StoredReport report = getStoredReport(reportId);
        if ("word".equalsIgnoreCase(format) || "docx".equalsIgnoreCase(format)) {
            return report.wordPath();
        }
        if ("pdf".equalsIgnoreCase(format)) {
            return report.pdfPath();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件格式: " + format);
    }

    private StoredReport getStoredReport(String reportId) {
        StoredReport report = reports.get(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "报告不存在: " + reportId);
        }
        return report;
    }

    private void validateRecords(String newsDate, List<NewsPoolRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据库中未查询到日期为 " + newsDate + " 的资讯");
        }
        if (records.size() < MIN_REQUIRED_ARTICLES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "日期 " + newsDate + " 下仅有 " + records.size() + " 条资讯，少于 19 条，无法生成日报"
            );
        }
    }

    private Path prepareReportDirectory(String reportId) {
        Path outputDirectory = reportProperties.resolveOutputDirectory();
        Path reportDirectory = outputDirectory.resolve(reportId);
        try {
            Files.createDirectories(reportDirectory);
            return reportDirectory;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建报告输出目录失败", ex);
        }
    }

    private String buildContent(String newsDate, List<NewsPoolRecord> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("目标报告日期：").append(newsDate).append(System.lineSeparator());
        builder.append("同日期资讯数量：").append(records.size()).append(System.lineSeparator());
        builder.append("说明：以下为数据库中该日期下的资讯池原始内容，请仅基于这些内容生成日报。")
                .append(System.lineSeparator()).append(System.lineSeparator());

        int index = 1;
        for (NewsPoolRecord record : records) {
            builder.append("[资讯").append(index++).append("]").append(System.lineSeparator());
            builder.append("标题：").append(nullSafe(record.title())).append(System.lineSeparator());
            builder.append("日期：").append(nullSafe(record.newsDate())).append(System.lineSeparator());
            builder.append("正文：").append(nullSafe(record.content())).append(System.lineSeparator()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "未提供" : value.trim();
    }

    private GeneratedReportResponse toResponse(StoredReport report) {
        return new GeneratedReportResponse(
                report.reportId(),
                report.reportTitle(),
                report.reportDate(),
                report.generatedContent(),
                report.articleCount(),
                report.generatedAt(),
                toFileDescriptor(report.reportId(), report.wordPath(), "WORD"),
                toFileDescriptor(report.reportId(), report.pdfPath(), "PDF")
        );
    }

    private ReportListItem toListItem(StoredReport report) {
        return new ReportListItem(
                report.reportId(),
                report.reportTitle(),
                report.reportDate(),
                report.articleCount(),
                report.generatedAt(),
                report.wordPath().getFileName().toString(),
                report.pdfPath().getFileName().toString()
        );
    }

    private ReportFileDescriptor toFileDescriptor(String reportId, Path path, String format) {
        String routeFormat = "WORD".equals(format) ? "word" : "pdf";
        return new ReportFileDescriptor(
                format,
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                "/api/reports/" + reportId + "/files/" + routeFormat
        );
    }

    private record StoredReport(
            String reportId,
            String reportTitle,
            LocalDate reportDate,
            String generatedContent,
            int articleCount,
            Instant generatedAt,
            Path wordPath,
            Path pdfPath
    ) {
    }
}
