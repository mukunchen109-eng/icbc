package com.icbc.financialinfo.modules.report.repository;

import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class ReportVersionRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Logger log = LoggerFactory.getLogger(ReportVersionRepository.class);
    private static final long FIRST_REPORT_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties reportProperties;

    public ReportVersionRepository(JdbcTemplate jdbcTemplate, ReportProperties reportProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportProperties = reportProperties;
    }

    public long upsertReportRecord(
            LocalDate reportDate,
            String reportTitle,
            String status,
            int locked,
            Long lockedBy,
            LocalDateTime lockedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        String tableName = resolveTableName();
        long reportId = nextReportId(tableName);
        String sql = "insert into " + tableName
                + " (id, report_date, report_title, status, locked, locked_by, locked_at, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on duplicate key update "
                + "report_date = values(report_date), "
                + "report_title = values(report_title), "
                + "status = values(status), "
                + "locked = values(locked), "
                + "locked_by = values(locked_by), "
                + "locked_at = values(locked_at), "
                + "updated_at = values(updated_at)";

        Object[] params = {
                reportId,
                Date.valueOf(reportDate),
                reportTitle,
                normalizeStatus(status),
                locked,
                lockedBy,
                lockedAt == null ? null : Timestamp.valueOf(lockedAt),
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt)
        };

        try {
            log.debug(
                    "准备写入 report: table={}, fixedId={}, reportDate={}, reportTitle={}, status={}, locked={}, lockedBy={}, lockedAt={}, createdAt={}, updatedAt={}",
                    tableName,
                    reportId,
                    reportDate,
                    reportTitle,
                    normalizeStatus(status),
                    locked,
                    lockedBy,
                    lockedAt,
                    createdAt,
                    updatedAt
            );
            jdbcTemplate.update(sql, params);
            log.debug("写入 report 成功: table={}, reportId={}", tableName, reportId);
            return reportId;
        } catch (DataAccessException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "写入 report 失败: table={}, sql={}, fixedId={}, reportDate={}, reportTitle={}, status={}, locked={}, lockedBy={}, lockedAt={}, createdAt={}, updatedAt={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    tableName,
                    sql,
                    reportId,
                    reportDate,
                    reportTitle,
                    normalizeStatus(status),
                    locked,
                    lockedBy,
                    lockedAt,
                    createdAt,
                    updatedAt,
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw ex;
        } catch (RuntimeException ex) {
            Throwable rootCause = rootCauseOf(ex);
            log.error(
                    "写入 report 发生未预期异常: table={}, sql={}, fixedId={}, reportDate={}, reportTitle={}, status={}, locked={}, lockedBy={}, lockedAt={}, createdAt={}, updatedAt={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    tableName,
                    sql,
                    reportId,
                    reportDate,
                    reportTitle,
                    normalizeStatus(status),
                    locked,
                    lockedBy,
                    lockedAt,
                    createdAt,
                    updatedAt,
                    ex.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "INITIAL_REVIEW" : status.trim();
    }

    private long nextReportId(String tableName) {
        List<Long> ids = jdbcTemplate.query(
                "select id from " + tableName + " order by id desc limit 1",
                (rs, rowNum) -> rs.getLong("id")
        );
        return ids.isEmpty() ? FIRST_REPORT_ID : ids.get(0) + 1;
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
