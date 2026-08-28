package com.icbc.financialinfo.modules.report.repository;

import com.icbc.financialinfo.modules.report.model.DifySummaryArticle;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Repository
public class ReportArticleRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Logger log = LoggerFactory.getLogger(ReportArticleRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties reportProperties;

    public ReportArticleRepository(JdbcTemplate jdbcTemplate, ReportProperties reportProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportProperties = reportProperties;
    }

    public void insertArticles(long reportId, List<DifySummaryArticle> articles, LocalDateTime createdAt) {
        if (articles == null || articles.isEmpty()) {
            throw new IllegalStateException("No Dify selected articles to insert");
        }

        String tableName = resolveTableName();
        String sql = "insert into " + tableName
                + " (report_id, news_id, category, title, summary_content, reason, select_type, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            log.debug("准备写入 report_article: table={}, reportId={}, articleCount={}, createdAt={}",
                    tableName, reportId, articles.size(), createdAt);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    DifySummaryArticle article = articles.get(i);
                    ps.setLong(1, reportId);
                    ps.setLong(2, Objects.requireNonNull(article.newsId(), "newsId"));
                    ps.setString(3, article.category());
                    ps.setString(4, article.title());
                    ps.setString(5, article.summaryContent());
                    ps.setString(6, article.reason());
                    ps.setString(7, article.selectType());
                    ps.setTimestamp(8, Timestamp.valueOf(createdAt));
                }

                @Override
                public int getBatchSize() {
                    return articles.size();
                }
            });
            log.debug("写入 report_article 成功: table={}, reportId={}, articleCount={}", tableName, reportId, articles.size());
        } catch (DataAccessException ex) {
            log.error("写入 report_article 失败: table={}, reportId={}, articleCount={}, createdAt={}, exceptionType={}, message={}",
                    tableName,
                    reportId,
                    articles.size(),
                    createdAt,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("写入 report_article 发生未预期异常: table={}, reportId={}, articleCount={}, createdAt={}, exceptionType={}, message={}",
                    tableName,
                    reportId,
                    articles.size(),
                    createdAt,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private String resolveTableName() {
        String tableName = reportProperties.getReportArticleTable();
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalStateException("app.report.report-article-table 配置非法");
        }
        return tableName;
    }
}
