package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.review.ReviewQueryModels.ArticleSource;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.AssignedTaskSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportArticle;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReviewTaskDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.VersionDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.VersionSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewQueryRepository {
    private static final String REVIEWABLE_STATUSES = "('INITIAL_REVIEW','FINAL_REVIEW')";
    private static final String REPORT_COLUMNS = """
            SELECT r.id, DATE_FORMAT(r.report_date, '%Y-%m-%d') AS report_date,
                   r.report_title, r.status, r.current_version_no, r.locked,
                   r.locked_by, DATE_FORMAT(r.locked_at, '%Y-%m-%d %H:%i:%s') AS locked_at,
                   DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                   DATE_FORMAT(r.updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at
              FROM report r
            """;

    private static final RowMapper<ReportSummary> REPORT_MAPPER = (rs, rowNum) ->
            new ReportSummary(
                    rs.getLong("id"), rs.getString("report_date"), rs.getString("report_title"),
                    rs.getString("status"), rs.getInt("current_version_no"), rs.getInt("locked"),
                    rs.getObject("locked_by", Long.class), rs.getString("locked_at"),
                    rs.getString("created_at"), rs.getString("updated_at"));

    private final JdbcTemplate jdbcTemplate;

    public ReviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReportSummary> findReviewPage(
            int pageNum, int pageSize, String reportDate, String status) {
        QueryParts query = reviewFilters(reportDate, status);
        query.parameters().add(pageSize);
        query.parameters().add((pageNum - 1) * pageSize);
        return jdbcTemplate.query(REPORT_COLUMNS + query.whereClause()
                        + " ORDER BY r.report_date DESC, r.id DESC LIMIT ? OFFSET ?",
                REPORT_MAPPER, query.parameters().toArray());
    }

    public long countReviewReports(String reportDate, String status) {
        QueryParts query = reviewFilters(reportDate, status);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report r" + query.whereClause(), Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public Optional<ReportSummary> findReport(long reportId) {
        return jdbcTemplate.query(REPORT_COLUMNS + " WHERE r.id = ?", REPORT_MAPPER, reportId)
                .stream().findFirst();
    }

    public List<AssignedTaskSummary> findAssignedTasks(
            long reviewerId, String stage, String status, int pageNum, int pageSize) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(reviewerId);
        parameters.add(stage);
        String statusSql = "";
        if (status != null && !status.isBlank()) {
            statusSql = " AND t.status=?";
            parameters.add(status.trim().toUpperCase());
        }
        parameters.add(pageSize);
        parameters.add((pageNum - 1) * pageSize);
        return jdbcTemplate.query("""
                SELECT t.id,t.report_id,t.version_id,v.version_no,t.review_stage,t.status,
                       DATE_FORMAT(r.report_date,'%Y-%m-%d') report_date,r.report_title,
                       DATE_FORMAT(t.submitted_at,'%Y-%m-%d %H:%i:%s') submitted_at,
                       DATE_FORMAT(t.completed_at,'%Y-%m-%d %H:%i:%s') completed_at,
                       v.word_file_path,v.pdf_file_path
                  FROM review_task t
                  JOIN report r ON r.id=t.report_id
                  JOIN report_version v ON v.id=t.version_id
                 WHERE t.reviewer_id=? AND t.review_stage=?
                """ + statusSql + " ORDER BY t.submitted_at DESC,t.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AssignedTaskSummary(
                        rs.getLong("id"), rs.getLong("report_id"), rs.getLong("version_id"),
                        rs.getInt("version_no"), rs.getString("review_stage"), rs.getString("status"),
                        rs.getString("report_date"), rs.getString("report_title"),
                        rs.getString("submitted_at"), rs.getString("completed_at"),
                        rs.getString("word_file_path"), rs.getString("pdf_file_path")),
                parameters.toArray());
    }

    public long countAssignedTasks(long reviewerId, String stage, String status) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(reviewerId);
        parameters.add(stage);
        String statusSql = "";
        if (status != null && !status.isBlank()) {
            statusSql = " AND status=?";
            parameters.add(status.trim().toUpperCase());
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE reviewer_id=? AND review_stage=?" + statusSql,
                Long.class, parameters.toArray());
        return count == null ? 0 : count;
    }

    public Optional<VersionDetail> findVersion(long reportId, int versionNo) {
        String sql = """
                SELECT id, version_no, version_type, word_file_path, pdf_file_path, created_by,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM report_version
                 WHERE report_id = ? AND version_no = ?
                 ORDER BY id DESC
                 LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new VersionDetail(
                        rs.getLong("id"), rs.getInt("version_no"), rs.getString("version_type"),
                        rs.getString("word_file_path"), rs.getString("pdf_file_path"),
                        rs.getLong("created_by"), rs.getString("created_at")), reportId, versionNo)
                .stream().findFirst();
    }

    public Optional<ReviewTaskDetail> findCurrentReviewTask(long reportId, long versionId) {
        String sql = """
                SELECT id, review_stage, reviewer_id, status,
                       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') AS submitted_at,
                       DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completed_at
                  FROM review_task
                 WHERE report_id = ? AND version_id = ?
                 ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END,
                          submitted_at DESC, id DESC
                 LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewTaskDetail(
                        rs.getLong("id"), rs.getString("review_stage"), rs.getLong("reviewer_id"),
                        rs.getString("status"), rs.getString("submitted_at"),
                        rs.getString("completed_at")), reportId, versionId)
                .stream().findFirst();
    }

    public boolean versionExists(long versionId) {
        return exists("SELECT COUNT(*) FROM report_version WHERE id = ?", versionId);
    }

    public List<ReportArticle> findArticles(long versionId) {
        String sql = """
                SELECT id, version_id, news_id, sequence_no, category, title, summary_content,
                       source_label, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM report_article
                 WHERE version_id = ?
                 ORDER BY sequence_no, id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportArticle(
                rs.getLong("id"), rs.getLong("version_id"), rs.getLong("news_id"),
                rs.getInt("sequence_no"), rs.getString("category"), rs.getString("title"),
                rs.getString("summary_content"), rs.getString("source_label"),
                rs.getString("created_at")), versionId);
    }

    public Optional<ArticleSource> findArticleSource(long articleId) {
        String sql = """
                SELECT a.id AS article_id, n.id AS news_id, n.source_row_id,
                       DATE_FORMAT(n.news_date, '%Y-%m-%d') AS news_date,
                       n.title, n.original_content, n.content, n.industry, n.area, n.content_hash
                  FROM report_article a
                  JOIN news_pool n ON n.id = a.news_id
                 WHERE a.id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ArticleSource(
                        rs.getLong("article_id"), rs.getLong("news_id"),
                        rs.getString("source_row_id"), rs.getString("news_date"),
                        rs.getString("title"), rs.getString("original_content"),
                        rs.getString("content"), rs.getString("industry"), rs.getString("area"),
                        rs.getString("content_hash")), articleId)
                .stream().findFirst();
    }

    public List<VersionSummary> findVersions(long reportId) {
        String sql = """
                SELECT id, version_no, version_type, created_by,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM report_version
                 WHERE report_id = ?
                 ORDER BY version_no, id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new VersionSummary(
                rs.getLong("id"), rs.getInt("version_no"), rs.getString("version_type"),
                rs.getLong("created_by"), rs.getString("created_at")), reportId);
    }

    private boolean exists(String sql, long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    private QueryParts reviewFilters(String reportDate, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        conditions.add("r.status IN " + REVIEWABLE_STATUSES);
        if (reportDate != null && !reportDate.isBlank()) {
            conditions.add("r.report_date = ?");
            parameters.add(reportDate.trim());
        }
        if (status != null && !status.isBlank()) {
            conditions.add("r.status = ?");
            parameters.add(status.trim().toUpperCase());
        }
        return new QueryParts(" WHERE " + String.join(" AND ", conditions), parameters);
    }

    private record QueryParts(String whereClause, List<Object> parameters) {}
}
