package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.model.DifyWorkflowRequest;
import com.icbc.financialinfo.modules.report.model.DifyNewsArticleInput;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.NewsPoolRecord;
import com.icbc.financialinfo.modules.report.model.ReportFileDescriptor;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import com.icbc.financialinfo.modules.report.repository.NewsPoolRepository;
import com.icbc.financialinfo.modules.report.repository.ReportVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
    private static final String REPORT_FILE_BASE_NAME = "每日资讯摘要";
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final DifyService difyService;
    private final WordService wordService;
    private final PdfService pdfService;
    private final ReportProperties reportProperties;
    private final NewsPoolRepository newsPoolRepository;
    private final ReportVersionRepository reportVersionRepository;
    private final Map<String, StoredReport> reports = new ConcurrentHashMap<>();

    public ReportService(
            DifyService difyService,
            WordService wordService,
            PdfService pdfService,
            ReportProperties reportProperties,
            NewsPoolRepository newsPoolRepository,
            ReportVersionRepository reportVersionRepository
    ) {
        this.difyService = difyService;
        this.wordService = wordService;
        this.pdfService = pdfService;
        this.reportProperties = reportProperties;
        this.newsPoolRepository = newsPoolRepository;
        this.reportVersionRepository = reportVersionRepository;
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
                ? "Daily summary - " + newsDate
                : request.reportTitle().trim();

        List<NewsPoolRecord> records = newsPoolRepository.findByNewsDate(newsDate);
        validateRecords(newsDate, records);

        List<DifyNewsArticleInput> articles = records.stream()
                .map(this::toDifyArticleInput)
                .toList();
        DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(articles);
        String reportId = UUID.randomUUID().toString().replace("-", "");
        Path reportDirectory = prepareReportDirectory(newsDate);
        String generatedContent = difyService.generateDailySummary(workflowRequest, reportDirectory);
        Path wordPath = wordService.writeDailySummary(
                reportDirectory,
                REPORT_FILE_BASE_NAME,
                reportTitle,
                reportDate,
                generatedContent
        );
        Path pdfPath = pdfService.writeDailySummary(
                reportDirectory,
                REPORT_FILE_BASE_NAME,
                reportTitle,
                reportDate,
                generatedContent
        );
        Instant generatedAt = Instant.now();

        persistReportRecord(reportId, reportDate, reportTitle, records.size(), generatedContent, wordPath, pdfPath, generatedAt);

        StoredReport report = new StoredReport(
                reportId,
                reportTitle,
                reportDate,
                generatedContent,
                records.size(),
                generatedAt,
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
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file format: " + format);
    }

    private StoredReport getStoredReport(String reportId) {
        StoredReport report = reports.get(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + reportId);
        }
        return report;
    }

    private void validateRecords(String newsDate, List<NewsPoolRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No news records found for date " + newsDate
            );
        }
        if (records.size() < MIN_REQUIRED_ARTICLES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date " + newsDate + " has only " + records.size() + " records, below the minimum threshold"
            );
        }
    }

    private Path prepareReportDirectory(String newsDate) {
        Path outputDirectory = reportProperties.resolveOutputDirectory();
        Path reportDirectory = outputDirectory.resolve(newsDate);
        try {
            Files.createDirectories(reportDirectory);
            return reportDirectory;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create report output directory", ex);
        }
    }

    private void persistReportRecord(
            String reportId,
            LocalDate reportDate,
            String reportTitle,
            int articleCount,
            String contentSnapshot,
            Path wordPath,
            Path pdfPath,
            Instant generatedAt
    ) {
        try {
            reportVersionRepository.insertReportRecord(
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    contentSnapshot,
                    wordPath.toAbsolutePath().toString(),
                    pdfPath.toAbsolutePath().toString(),
                    generatedAt
            );
        } catch (DataAccessException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "Failed to write generated_report: reportId={}, reportDate={}, reportTitle={}, articleCount={}, wordPath={}, pdfPath={}, generatedAt={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    wordPath.toAbsolutePath(),
                    pdfPath.toAbsolutePath(),
                    generatedAt,
                    reportProperties.getReportTable(),
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to write generated_report; check server logs for the database error details",
                    ex
            );
        } catch (RuntimeException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "Unexpected error while writing generated_report: reportId={}, reportDate={}, reportTitle={}, articleCount={}, wordPath={}, pdfPath={}, generatedAt={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    wordPath.toAbsolutePath(),
                    pdfPath.toAbsolutePath(),
                    generatedAt,
                    reportProperties.getReportTable(),
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to write generated_report; check server logs for the exception details",
                    ex
            );
        }
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private DifyNewsArticleInput toDifyArticleInput(NewsPoolRecord record) {
        return new DifyNewsArticleInput(
                nullSafe(record.contentHash()),
                nullSafe(record.title()),
                nullSafe(record.content()),
                nullSafe(record.industry()),
                nullSafe(record.area())
        );
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "N/A" : value.trim();
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
