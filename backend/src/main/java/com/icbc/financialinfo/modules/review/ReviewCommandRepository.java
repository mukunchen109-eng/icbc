package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplacementArticle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewCommandRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewCommandRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TaskState> findTaskForUpdate(long taskId) {
        String sql = """
                SELECT id, report_id, version_id, review_stage, reviewer_id, status
                  FROM review_task
                 WHERE id = ?
                   FOR UPDATE
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TaskState(
                        rs.getLong("id"), rs.getLong("report_id"), rs.getLong("version_id"),
                        rs.getString("review_stage"), rs.getLong("reviewer_id"),
                        rs.getString("status")), taskId)
                .stream().findFirst();
    }

    public Optional<TaskState> findTask(long taskId) {
        String sql = """
                SELECT id, report_id, version_id, review_stage, reviewer_id, status
                  FROM review_task
                 WHERE id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TaskState(
                        rs.getLong("id"), rs.getLong("report_id"), rs.getLong("version_id"),
                        rs.getString("review_stage"), rs.getLong("reviewer_id"),
                        rs.getString("status")), taskId)
                .stream().findFirst();
    }

    public Optional<ReportState> lockReport(long reportId) {
        String sql = """
                SELECT id, DATE_FORMAT(report_date, '%Y-%m-%d') AS report_date,
                       status, current_version_no, locked, locked_by
                  FROM report
                 WHERE id = ?
                   FOR UPDATE
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportState(
                        rs.getLong("id"), rs.getString("report_date"), rs.getString("status"),
                        rs.getInt("current_version_no"), rs.getInt("locked"),
                        rs.getObject("locked_by", Long.class)), reportId)
                .stream().findFirst();
    }

    public Optional<ReportState> findReport(long reportId) {
        String sql = """
                SELECT id, DATE_FORMAT(report_date, '%Y-%m-%d') AS report_date,
                       status, current_version_no, locked, locked_by
                  FROM report
                 WHERE id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportState(
                        rs.getLong("id"), rs.getString("report_date"), rs.getString("status"),
                        rs.getInt("current_version_no"), rs.getInt("locked"),
                        rs.getObject("locked_by", Long.class)), reportId)
                .stream().findFirst();
    }

    public Optional<VersionState> findVersion(long reportId, int versionNo) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, version_no
                          FROM report_version
                         WHERE report_id = ? AND version_no = ?
                        """,
                (rs, rowNum) -> new VersionState(
                        rs.getLong("id"), rs.getLong("report_id"), rs.getInt("version_no")),
                reportId, versionNo).stream().findFirst();
    }

    public Optional<ArticleState> findArticle(long articleId) {
        String sql = """
                SELECT a.id, a.version_id, v.report_id, a.news_id, a.sequence_no,
                       a.category, a.title, a.summary_content, a.source_label
                  FROM report_article a
                  JOIN report_version v ON v.id = a.version_id
                 WHERE a.id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ArticleState(
                        rs.getLong("id"), rs.getLong("version_id"), rs.getLong("report_id"),
                        rs.getLong("news_id"), rs.getInt("sequence_no"),
                        rs.getString("category"), rs.getString("title"),
                        rs.getString("summary_content"), rs.getString("source_label")), articleId)
                .stream().findFirst();
    }

    public Optional<ArticleState> findArticle(long versionId, int sequenceNo) {
        String sql = """
                SELECT a.id, a.version_id, v.report_id, a.news_id, a.sequence_no,
                       a.category, a.title, a.summary_content, a.source_label
                  FROM report_article a
                  JOIN report_version v ON v.id = a.version_id
                 WHERE a.version_id = ? AND a.sequence_no = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ArticleState(
                        rs.getLong("id"), rs.getLong("version_id"), rs.getLong("report_id"),
                        rs.getLong("news_id"), rs.getInt("sequence_no"),
                        rs.getString("category"), rs.getString("title"),
                        rs.getString("summary_content"), rs.getString("source_label")),
                versionId, sequenceNo).stream().findFirst();
    }

    public Optional<NewsState> findNews(long newsId) {
        String sql = """
                SELECT id, DATE_FORMAT(news_date, '%Y-%m-%d') AS news_date,
                       title, content, industry, area
                  FROM news_pool
                 WHERE id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new NewsState(
                        rs.getLong("id"), rs.getString("news_date"), rs.getString("title"),
                        rs.getString("content"), rs.getString("industry"), rs.getString("area")),
                newsId).stream().findFirst();
    }

    public long createVersion(long reportId, int versionNo, String versionType, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO report_version(
                        report_id, version_no, version_type,
                        word_file_path, pdf_file_path, created_by, created_at)
                    VALUES (?, ?, ?, NULL, NULL, ?, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, reportId);
            statement.setInt(2, versionNo);
            statement.setString(3, versionType);
            statement.setLong(4, operatorId);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "报告版本ID");
    }

    public void copyArticlesWithModification(long sourceVersionId, long targetVersionId,
                                             long sourceArticleId, String title,
                                             String summaryContent, String sourceLabel) {
        jdbcTemplate.update("""
                INSERT INTO report_article(
                    version_id, news_id, sequence_no, category, title,
                    summary_content, source_label, created_at)
                SELECT ?, news_id, sequence_no, category,
                       CASE WHEN id = ? THEN ? ELSE title END,
                       CASE WHEN id = ? THEN ? ELSE summary_content END,
                       CASE WHEN id = ? THEN ? ELSE source_label END,
                       CURRENT_TIMESTAMP
                  FROM report_article
                 WHERE version_id = ?
                 ORDER BY sequence_no, id
                """, targetVersionId,
                sourceArticleId, title,
                sourceArticleId, summaryContent,
                sourceArticleId, sourceLabel,
                sourceVersionId);
    }

    public void copyArticlesWithReplacement(long sourceVersionId, long targetVersionId,
                                            long sourceArticleId, NewsState news) {
        jdbcTemplate.update("""
                INSERT INTO report_article(
                    version_id, news_id, sequence_no, category, title,
                    summary_content, source_label, created_at)
                SELECT ?,
                       CASE WHEN id = ? THEN ? ELSE news_id END,
                       sequence_no, category,
                       CASE WHEN id = ? THEN ? ELSE title END,
                       CASE WHEN id = ? THEN ? ELSE summary_content END,
                       source_label, CURRENT_TIMESTAMP
                  FROM report_article
                 WHERE version_id = ?
                 ORDER BY sequence_no, id
                """, targetVersionId,
                sourceArticleId, news.id(),
                sourceArticleId, news.title(),
                sourceArticleId, news.content(),
                sourceVersionId);
    }

    public void advanceReportVersion(long reportId, int versionNo, long operatorId) {
        jdbcTemplate.update("""
                UPDATE report
                   SET current_version_no = ?,
                       locked = 1,
                       locked_by = ?,
                       locked_at = CASE WHEN locked = 0 THEN CURRENT_TIMESTAMP ELSE locked_at END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, versionNo, operatorId, reportId);
    }

    public long insertReviewRecord(long taskId, long reportId, long versionId, Long articleId,
                                   long operatorId, String actionType, String beforeContent,
                                   String afterContent, String reason, String commentText) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO review_record(
                        review_task_id, report_id, version_id, article_id, operator_id,
                        action_type, before_content, after_content, reason,
                        comment_text, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, taskId);
            statement.setLong(2, reportId);
            statement.setLong(3, versionId);
            if (articleId == null) statement.setNull(4, java.sql.Types.BIGINT);
            else statement.setLong(4, articleId);
            statement.setLong(5, operatorId);
            statement.setString(6, actionType);
            statement.setString(7, beforeContent);
            statement.setString(8, afterContent);
            statement.setString(9, reason);
            statement.setString(10, commentText);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "审核记录ID");
    }

    public List<ReplacementArticle> findReplacementArticles(
            long currentVersionId, String reportDate, String category, String keyword) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        conditions.add("n.news_date = ?");
        parameters.add(reportDate);
        conditions.add("NOT EXISTS (SELECT 1 FROM report_article a "
                + "WHERE a.version_id = ? AND a.news_id = n.id)");
        parameters.add(currentVersionId);

        String industry = categoryIndustry(category);
        if (industry != null) {
            conditions.add("n.industry = ?");
            parameters.add(industry);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("n.title LIKE ?");
            parameters.add("%" + keyword.trim() + "%");
        }

        String sql = """
                SELECT n.id AS news_id, DATE_FORMAT(n.news_date, '%Y-%m-%d') AS news_date,
                       n.title, n.industry, n.area
                  FROM news_pool n
                 WHERE __CONDITIONS__
                 ORDER BY n.id
                 LIMIT 100
                """.replace("__CONDITIONS__", String.join(" AND ", conditions));
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReplacementArticle(
                rs.getLong("news_id"), rs.getString("news_date"), rs.getString("title"),
                rs.getString("industry"), rs.getString("area")), parameters.toArray());
    }

    private String categoryIndustry(String category) {
        if (category == null || category.isBlank()) return null;
        return switch (category.trim().toUpperCase()) {
            case "FINANCE" -> "金融";
            case "MACRO" -> "宏观经济";
            case "POLICY" -> "政策";
            default -> "__UNKNOWN_CATEGORY__";
        };
    }

    private long generatedId(KeyHolder keyHolder, String name) {
        if (keyHolder.getKey() == null) throw new IllegalStateException("数据库未返回" + name);
        return keyHolder.getKey().longValue();
    }

    public record TaskState(Long id, Long reportId, Long versionId, String reviewStage,
                            Long reviewerId, String status) {}
    public record ReportState(Long id, String reportDate, String status,
                              Integer currentVersionNo, Integer locked, Long lockedBy) {}
    public record VersionState(Long id, Long reportId, Integer versionNo) {}
    public record ArticleState(Long id, Long versionId, Long reportId, Long newsId,
                               Integer sequenceNo, String category, String title,
                               String summaryContent, String sourceLabel) {}
    public record NewsState(Long id, String newsDate, String title, String content,
                            String industry, String area) {}
}
