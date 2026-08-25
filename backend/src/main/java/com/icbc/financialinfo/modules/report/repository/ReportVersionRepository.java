package com.icbc.financialinfo.modules.report.repository;

import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

@Repository
public class ReportVersionRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Logger log = LoggerFactory.getLogger(ReportVersionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties reportProperties;

    public ReportVersionRepository(JdbcTemplate jdbcTemplate, ReportProperties reportProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportProperties = reportProperties;
    }

    public void insertReportRecord(
            String reportId,
            LocalDate reportDate,
            String reportTitle,
            int articleCount,
            String contentSnapshot,
            String wordFilePath,
            String pdfFilePath,
            Instant createdAt
    ) {
        String tableName = resolveTableName();
        String sql = "insert into " + tableName
                + " (report_id, report_date, report_title, article_count, content_snapshot, word_file_path, pdf_file_path, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?)";
        Object[] params = {
                reportId,
                Date.valueOf(reportDate),
                reportTitle,
                articleCount,
                contentSnapshot,
                wordFilePath,
                pdfFilePath,
                Timestamp.from(createdAt)
        };

        try {
            log.debug(
                    "准备写入 generated_report: table={}, reportId={}, reportDate={}, reportTitle={}, articleCount={}, wordFilePath={}, pdfFilePath={}, createdAt={}",
                    tableName,
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    wordFilePath,
                    pdfFilePath,
                    createdAt
            );
            jdbcTemplate.update(sql, params);
            log.debug("写入 generated_report 成功: table={}, reportId={}", tableName, reportId);
        } catch (DataAccessException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "写入 generated_report 失败: table={}, sql={}, reportId={}, reportDate={}, reportTitle={}, articleCount={}, wordFilePath={}, pdfFilePath={}, createdAt={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    tableName,
                    sql,
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    wordFilePath,
                    pdfFilePath,
                    createdAt,
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw ex;
        } catch (RuntimeException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "写入 generated_report 发生未预期异常: table={}, sql={}, reportId={}, reportDate={}, reportTitle={}, articleCount={}, wordFilePath={}, pdfFilePath={}, createdAt={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    tableName,
                    sql,
                    reportId,
                    reportDate,
                    reportTitle,
                    articleCount,
                    wordFilePath,
                    pdfFilePath,
                    createdAt,
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private String resolveTableName() {
        String tableName = reportProperties.getReportTable();
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalStateException("app.report.report-table 配置非法");
        }
        return tableName;
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
