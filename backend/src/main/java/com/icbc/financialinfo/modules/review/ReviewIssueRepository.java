package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.review.ReviewIssueModels.ReviewIssue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ReviewIssueRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewIssueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean versionExists(long versionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_version WHERE id = ?", Integer.class, versionId);
        return count != null && count > 0;
    }

    public List<ReviewIssue> findIssues(long versionId) {
        String sql = """
                SELECT id, version_id, article_id,
                       CASE issue_type
                           WHEN 'SENSITIVE' THEN 'SENSITIVE_CONTENT'
                           WHEN 'DATA_MISMATCH' THEN 'DATA_INCONSISTENCY'
                           ELSE issue_type
                       END AS issue_type,
                       matched_text, start_offset, end_offset, message, resolved, resolved_by,
                       DATE_FORMAT(resolved_at, '%Y-%m-%d %H:%i:%s') AS resolved_at,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM review_issue
                 WHERE version_id = ?
                 ORDER BY article_id, start_offset, id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewIssue(
                rs.getLong("id"), rs.getLong("version_id"), rs.getLong("article_id"),
                rs.getString("issue_type"), rs.getString("matched_text"),
                rs.getInt("start_offset"), rs.getInt("end_offset"), rs.getString("message"),
                rs.getInt("resolved"), rs.getObject("resolved_by", Long.class),
                rs.getString("resolved_at"), rs.getString("created_at")), versionId);
    }

    public List<CheckArticle> findCheckArticles(long versionId) {
        String sql = """
                SELECT a.id AS article_id, a.summary_content, n.content AS source_content
                  FROM report_article a
                  JOIN news_pool n ON n.id = a.news_id
                 WHERE a.version_id = ?
                 ORDER BY a.sequence_no, a.id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CheckArticle(
                rs.getLong("article_id"), rs.getString("summary_content"),
                rs.getString("source_content")), versionId);
    }

    public void deleteIssues(long versionId) {
        jdbcTemplate.update("DELETE FROM review_issue WHERE version_id = ?", versionId);
    }

    public void insertIssue(long versionId, long articleId, String issueType,
                            String matchedText, int startOffset, int endOffset, String message) {
        jdbcTemplate.update("""
                INSERT INTO review_issue(
                    version_id, article_id, issue_type, matched_text,
                    start_offset, end_offset, message, resolved, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)
                """, versionId, articleId, issueType, matchedText,
                startOffset, endOffset, message);
    }

    public Optional<IssueState> lockIssue(long issueId) {
        return jdbcTemplate.query("""
                        SELECT id, resolved
                          FROM review_issue
                         WHERE id = ?
                           FOR UPDATE
                        """,
                (rs, rowNum) -> new IssueState(rs.getLong("id"), rs.getInt("resolved")), issueId)
                .stream().findFirst();
    }

    public void resolveIssue(long issueId, long operatorId) {
        jdbcTemplate.update("""
                UPDATE review_issue
                   SET resolved = 1, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, operatorId, issueId);
    }

    public record CheckArticle(Long articleId, String summaryContent, String sourceContent) {}
    public record IssueState(Long id, Integer resolved) {}
}
