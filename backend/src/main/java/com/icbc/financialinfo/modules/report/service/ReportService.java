package com.icbc.financialinfo.modules.report.service;

import com.icbc.financialinfo.modules.report.model.DifyNewsArticleInput;
import com.icbc.financialinfo.modules.report.model.DifySummaryArticle;
import com.icbc.financialinfo.modules.report.model.DifyWorkflowRequest;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.NewsPoolRecord;
import com.icbc.financialinfo.modules.report.model.ReportFileDescriptor;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import com.icbc.financialinfo.modules.report.repository.NewsPoolRepository;
import com.icbc.financialinfo.modules.report.repository.ReportArticleRepository;
import com.icbc.financialinfo.modules.report.repository.ReportVersionRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

  private static final int MIN_REQUIRED_ARTICLES = 1;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern(
    "yyyyMMdd"
  );
  private static final String REPORT_FILE_BASE_NAME = "每日经济金融信息";
  private static final String REPORT_STATUS = "INITIAL_REVIEW";
  private static final int REPORT_LOCKED = 0;
  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final DifyService difyService;
  private final ReportProperties reportProperties;
  private final NewsPoolRepository newsPoolRepository;
  private final ReportVersionRepository reportVersionRepository;
  private final ReportArticleRepository reportArticleRepository;
  private final Map<String, StoredReport> reports = new ConcurrentHashMap<>();

  public ReportService(
    DifyService difyService,
    ReportProperties reportProperties,
    NewsPoolRepository newsPoolRepository,
    ReportVersionRepository reportVersionRepository,
    ReportArticleRepository reportArticleRepository
  ) {
    this.difyService = difyService;
    this.reportProperties = reportProperties;
    this.newsPoolRepository = newsPoolRepository;
    this.reportVersionRepository = reportVersionRepository;
    this.reportArticleRepository = reportArticleRepository;
  }

  public List<ReportListItem> listReports() {
    return reports
      .values()
      .stream()
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

    List<NewsPoolRecord> records = newsPoolRepository.findByNewsDate(newsDate);
    validateRecords(newsDate, records);

    String reportFileBaseName = buildReportFileBaseName(reportDate);
    String reportTitle = request.reportTitle() == null || request.reportTitle().isBlank()
      ? reportFileBaseName
      : request.reportTitle().trim();

    List<DifyNewsArticleInput> articles = buildDifyArticles(records);
    DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(articles);
    String reportId = UUID.randomUUID().toString().replace("-", "");
    String generatedContent = difyService.generateDailySummary(workflowRequest);
    Instant generatedAt = Instant.now();
    LocalDateTime now = LocalDateTime.now();
    List<DifySummaryArticle> summaryArticles = DifySummaryArticleParser.parseSelectedArticles(
      generatedContent
    );

    long persistedReportId = persistReportRecord(reportDate, reportTitle, now);
    persistReportArticles(persistedReportId, summaryArticles, now);

    StoredReport report = new StoredReport(
      reportId,
      reportTitle,
      reportDate,
      generatedContent,
      records.size(),
      generatedAt,
      null,
      null
    );
    reports.put(reportId, report);
    return toResponse(report);
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

  private long persistReportRecord(LocalDate reportDate, String reportTitle, LocalDateTime now) {
    try {
      return reportVersionRepository.upsertReportRecord(
        reportDate,
        reportTitle,
        REPORT_STATUS,
        REPORT_LOCKED,
        null,
        null,
        now,
        now
      );
    } catch (DataAccessException ex) {
      Throwable rootCause = rootCauseOf(ex);
      log.error(
        "Failed to write report table: reportDate={}, reportTitle={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
        reportDate,
        reportTitle,
        reportProperties.getReportTable(),
        ex.getClass().getName(),
        rootCause.getClass().getName(),
        rootCause.getMessage(),
        ex
      );
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to write report table; check server logs for the database error details",
        ex
      );
    } catch (RuntimeException ex) {
      Throwable rootCause = rootCauseOf(ex);
      log.error(
        "Unexpected error while writing report table: reportDate={}, reportTitle={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
        reportDate,
        reportTitle,
        reportProperties.getReportTable(),
        ex.getClass().getName(),
        rootCause.getClass().getName(),
        rootCause.getMessage(),
        ex
      );
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to write report table; check server logs for the exception details",
        ex
      );
    }
  }

  private void persistReportArticles(
    long reportId,
    List<DifySummaryArticle> summaryArticles,
    LocalDateTime now
  ) {
    try {
      reportArticleRepository.insertArticles(reportId, summaryArticles, now);
    } catch (DataAccessException ex) {
      Throwable rootCause = rootCauseOf(ex);
      log.error(
        "Failed to write report_article table: reportId={}, articleCount={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
        reportId,
        summaryArticles == null ? 0 : summaryArticles.size(),
        reportProperties.getReportArticleTable(),
        ex.getClass().getName(),
        rootCause.getClass().getName(),
        rootCause.getMessage(),
        ex
      );
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to write report_article table; check server logs for the database error details",
        ex
      );
    } catch (RuntimeException ex) {
      Throwable rootCause = rootCauseOf(ex);
      log.error(
        "Unexpected error while writing report_article table: reportId={}, articleCount={}, table={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
        reportId,
        summaryArticles == null ? 0 : summaryArticles.size(),
        reportProperties.getReportArticleTable(),
        ex.getClass().getName(),
        rootCause.getClass().getName(),
        rootCause.getMessage(),
        ex
      );
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to write report_article table; check server logs for the exception details",
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
      nullSafe(record.dailySeq()),
      nullSafe(record.title()),
      nullSafe(record.content()),
      nullSafe(record.industry()),
      nullSafe(record.area())
    );
  }

  private List<DifyNewsArticleInput> buildDifyArticles(List<NewsPoolRecord> records) {
    List<DifyNewsArticleInput> articles = new java.util.ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      NewsPoolRecord record = records.get(i);
      articles.add(
        new DifyNewsArticleInput(
          nullSafe(record.dailySeq()),
          nullSafe(record.title()),
          nullSafe(record.content()),
          nullSafe(record.industry()),
          nullSafe(record.area())
        )
      );
    }
    return List.copyOf(articles);
  }

  private String nullSafe(String value) {
    return value == null || value.isBlank() ? "N/A" : value.trim();
  }

  private String buildReportFileBaseName(LocalDate reportDate) {
    return REPORT_FILE_BASE_NAME + "-" + reportDate.format(FILE_DATE_FORMATTER);
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
      report.wordPath() == null ? null : report.wordPath().getFileName().toString(),
      report.pdfPath() == null ? null : report.pdfPath().getFileName().toString()
    );
  }

  private ReportFileDescriptor toFileDescriptor(String reportId, Path path, String format) {
    if (path == null) {
      return null;
    }
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
  ) {}
}
