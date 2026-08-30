package com.icbc.financialinfo.modules.dashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardRepository {

  private final JdbcTemplate jdbc;

  public AdminDashboardRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ReportSnapshot> todayReport() {
    return jdbc
      .query("""
      SELECT id, report_title, status FROM report
       WHERE report_date = CURRENT_DATE
       ORDER BY updated_at DESC, id DESC LIMIT 1
      """, (rs, n) -> new ReportSnapshot(rs.getLong("id"), rs.getString("report_title"), rs.getString("status")))
      .stream()
      .findFirst();
  }

  public Optional<JobSnapshot> todayJob() {
    return jdbc
      .query("""
      SELECT status, message, DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s') AS started_at
        FROM collection_job WHERE target_date = CURRENT_DATE
       ORDER BY id DESC LIMIT 1
      """, (rs, n) -> new JobSnapshot(rs.getString("status"), rs.getString("message"), rs.getString("started_at")))
      .stream()
      .findFirst();
  }

  public Map<String, Long> reviewCounts() {
    Map<String, Long> counts = emptyCounts("INITIAL", "FINAL", "APPROVED", "ARCHIVED");
    jdbc.query(
      """
      SELECT CASE
               WHEN status LIKE 'INITIAL_%' THEN 'INITIAL'
               WHEN status IN ('FINAL_APPROVED', 'FINALIZED') THEN 'APPROVED'
               WHEN status IN ('FINAL_ARCHIVED', 'ARCHIVED') THEN 'ARCHIVED'
               WHEN status LIKE 'FINAL_%' THEN 'FINAL'
             END AS category, COUNT(*) AS amount
        FROM report
       WHERE status LIKE 'INITIAL_%' OR status LIKE 'FINAL_%' OR status IN ('FINALIZED', 'ARCHIVED')
       GROUP BY category
      """,
      (RowCallbackHandler) rs -> counts.put(rs.getString("category"), rs.getLong("amount"))
    );
    return counts;
  }

  public Map<String, Long> distributionCounts() {
    Map<String, Long> counts = emptyCounts("SUCCESS", "FAILED");
    jdbc.query(
      "SELECT mail_status AS category, COUNT(*) AS amount FROM mail_log WHERE mail_status IN ('SUCCESS','FAILED') GROUP BY mail_status",
      (RowCallbackHandler) rs -> counts.put(rs.getString("category"), rs.getLong("amount"))
    );
    return counts;
  }

  public Map<String, Long> taskCounts() {
    Map<String, Long> counts = emptyCounts("SUCCESS", "FAILED");
    jdbc.query(
      """
      SELECT CASE WHEN status IN ('SUCCESS','COMPLETED') THEN 'SUCCESS' WHEN status='FAILED' THEN 'FAILED' END AS category,
             COUNT(*) AS amount
        FROM collection_job WHERE status IN ('SUCCESS','COMPLETED','FAILED') GROUP BY category
      """,
      (RowCallbackHandler) rs -> counts.put(rs.getString("category"), rs.getLong("amount"))
    );
    return counts;
  }

  public Map<String, Long> userCounts() {
    Map<String, Long> counts = emptyCounts("ADMIN", "INFO_MANAGER", "DEPT_MANAGER");
    jdbc.query(
      """
      SELECT r.role_code AS category, COUNT(u.id) AS amount
        FROM sys_role r LEFT JOIN sys_user u ON u.role_id=r.id
       WHERE r.role_code IN ('ADMIN','INFO_MANAGER','DEPT_MANAGER') GROUP BY r.role_code
      """,
      (RowCallbackHandler) rs -> counts.put(rs.getString("category"), rs.getLong("amount"))
    );
    return counts;
  }

  public Optional<LatestSnapshot> latestReport() {
    return jdbc
      .query("""
      SELECT report_title AS title,status,DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS occurred_at
        FROM report
       WHERE report_date=CURRENT_DATE
       ORDER BY updated_at DESC,id DESC LIMIT 1
      """, (rs, n) -> new LatestSnapshot(rs.getString("title"), rs.getString("status"), rs.getString("occurred_at")))
      .stream()
      .findFirst();
  }

  public Optional<LatestSnapshot> latestMail() {
    return jdbc
      .query("""
      SELECT COALESCE(r.report_title,mt.subject) AS title,mt.mail_status AS status,
             DATE_FORMAT(COALESCE(mt.completed_at,mt.created_at),'%Y-%m-%d %H:%i:%s') AS occurred_at
        FROM mail_task mt LEFT JOIN report r ON r.id=mt.report_id
       ORDER BY COALESCE(mt.completed_at,mt.created_at) DESC,mt.id DESC LIMIT 1
      """, (rs, n) -> new LatestSnapshot(rs.getString("title"), rs.getString("status"), rs.getString("occurred_at")))
      .stream()
      .findFirst();
  }

  public Optional<LatestSnapshot> latestJob() {
    return jdbc
      .query("""
      SELECT CONCAT('资讯采集 · ',DATE_FORMAT(target_date,'%Y-%m-%d')) AS title,status,
             DATE_FORMAT(COALESCE(finished_at,started_at),'%Y-%m-%d %H:%i:%s') AS occurred_at
        FROM collection_job ORDER BY target_date DESC,id DESC LIMIT 1
      """, (rs, n) -> new LatestSnapshot(rs.getString("title"), rs.getString("status"), rs.getString("occurred_at")))
      .stream()
      .findFirst();
  }

  private Map<String, Long> emptyCounts(String... keys) {
    Map<String, Long> counts = new LinkedHashMap<>();
    List.of(keys).forEach(key -> counts.put(key, 0L));
    return counts;
  }

  public record ReportSnapshot(long id, String title, String status) {}

  public record JobSnapshot(String status, String message, String startedAt) {}

  public record LatestSnapshot(String title, String status, String occurredAt) {}
}
