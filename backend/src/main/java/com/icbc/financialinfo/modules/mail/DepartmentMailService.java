package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Service
public class DepartmentMailService {
    private final JdbcTemplate jdbcTemplate;

    public DepartmentMailService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecipientOption> listRecipients() {
        return jdbcTemplate.query("""
                SELECT id,recipient_name,recipient_email
                  FROM mail_recipient
                 ORDER BY id
                """, (rs, rowNum) -> new RecipientOption(
                rs.getLong("id"), rs.getString("recipient_name"), rs.getString("recipient_email")));
    }

    public RecipientOption addRecipient(long managerId, String name, String email) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mail_recipient(recipient_name,recipient_email,created_by,created_at,updated_at) " +
                                "VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name.trim());
                statement.setString(2, email.trim());
                statement.setLong(3, managerId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new DepartmentBusinessException(409, "该收件邮箱已经存在");
        }
        if (keyHolder.getKey() == null) throw new DepartmentBusinessException(500, "数据库未返回收件人ID");
        return new RecipientOption(keyHolder.getKey().longValue(), name.trim(), email.trim());
    }

    @Transactional
    public CreateResult create(long managerId, CreateRequest request) {
        ReportForMail report = findReport(request.reportId(), request.versionId());
        Integer approved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE report_id=? AND version_id=? AND review_stage='FINAL' " +
                        "AND reviewer_id=? AND status='APPROVED'",
                Integer.class, request.reportId(), request.versionId(), managerId);
        if (approved == null || approved == 0) {
            throw new DepartmentBusinessException(409, "报告尚未通过当前负责人的终审，不能创建发送任务");
        }
        for (Recipient recipient : request.recipients()) {
            Integer validRecipient = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mail_recipient WHERE recipient_name=? AND recipient_email=?",
                    Integer.class, recipient.name().trim(), recipient.email().trim());
            if (validRecipient == null || validRecipient == 0) {
                throw new DepartmentBusinessException(400, "收件人不存在：" + recipient.email());
            }
        }
        List<CreateResult> existing = jdbcTemplate.query("""
                SELECT m.id,m.status,COUNT(l.id) recipient_count
                  FROM mail_task m LEFT JOIN mail_log l ON l.mail_task_id=m.id
                 WHERE m.report_id=? AND m.version_id=? AND m.created_by=?
                   AND m.status IN ('PENDING','FAILED','PARTIAL_FAILED')
                 GROUP BY m.id,m.status ORDER BY m.id DESC LIMIT 1
                """, (rs, rowNum) -> new CreateResult(
                rs.getLong("id"), rs.getString("status"), rs.getInt("recipient_count")),
                request.reportId(), request.versionId(), managerId);
        if (!existing.isEmpty()) {
            long existingId = existing.get(0).id();
            jdbcTemplate.update("DELETE FROM mail_log WHERE mail_task_id=?", existingId);
            jdbcTemplate.update("UPDATE mail_task SET subject=?,mail_body=?,status='PENDING',completed_at=NULL WHERE id=?",
                    request.subject().trim(), request.mailBody(), existingId);
            insertRecipients(existingId, request.recipients());
            return new CreateResult(existingId, "PENDING", request.recipients().size());
        }
        Integer completed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_task WHERE report_id=? AND version_id=? AND status IN ('SENDING','COMPLETED','SUCCESS')",
                Integer.class, request.reportId(), request.versionId());
        if (completed != null && completed > 0) {
            throw new DepartmentBusinessException(409, "该报告已经发送或正在发送，请勿重复创建");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO mail_task(report_id,version_id,subject,mail_body,status,created_by,created_at) " +
                            "VALUES(?,?,?,?, 'PENDING', ?, CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, report.reportId());
            statement.setLong(2, report.versionId());
            statement.setString(3, request.subject().trim());
            statement.setString(4, request.mailBody());
            statement.setLong(5, managerId);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) throw new DepartmentBusinessException(500, "数据库未返回邮件任务ID");
        long taskId = keyHolder.getKey().longValue();
        insertRecipients(taskId, request.recipients());
        return new CreateResult(taskId, "PENDING", request.recipients().size());
    }

    private void insertRecipients(long taskId, List<Recipient> recipients) {
        for (Recipient recipient : recipients) {
            jdbcTemplate.update(
                    "INSERT INTO mail_log(mail_task_id,recipient_name,recipient_email,status,retry_count,created_at,updated_at) " +
                            "VALUES(?,?,?,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    taskId, recipient.name().trim(), recipient.email().trim());
        }
    }

    @Transactional
    public SendResult send(long managerId, long taskId) {
        MailTask task = jdbcTemplate.query("""
                SELECT m.id,m.report_id,m.version_id,m.subject,m.mail_body,m.status,m.created_by,
                       m.created_at
                  FROM mail_task m
                 WHERE m.id=?
                """, (rs, rowNum) -> new MailTask(
                rs.getLong("id"), rs.getLong("report_id"), rs.getLong("version_id"),
                rs.getString("subject"), rs.getString("mail_body"), rs.getString("status"),
                rs.getLong("created_by")), taskId)
                .stream().findFirst().orElseThrow(() -> new DepartmentBusinessException(404, "邮件任务不存在"));
        if (task.createdBy() != managerId) throw new DepartmentBusinessException(403, "无权发送其他用户创建的邮件任务");
        if (!"PENDING".equals(task.status()) && !"PARTIAL_FAILED".equals(task.status()) && !"FAILED".equals(task.status())) {
            throw new DepartmentBusinessException(409, "邮件任务已完成或正在发送，请勿重复发送");
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_log WHERE mail_task_id=?", Integer.class, taskId);
        int totalCount = total == null ? 0 : total;
        if (totalCount == 0) {
            throw new DepartmentBusinessException(409, "邮件任务没有收件人发送明细，不能完成发送");
        }
        int transitioned = jdbcTemplate.update(
                "UPDATE mail_task SET status='SENDING' WHERE id=? AND status IN ('PENDING','FAILED','PARTIAL_FAILED')", taskId);
        if (transitioned == 0) {
            throw new DepartmentBusinessException(409, "邮件任务状态已经变化，请刷新后重试");
        }
        int processed = jdbcTemplate.update("""
                UPDATE mail_log
                   SET status='SUCCESS',retry_count=retry_count,error_message=NULL,
                       provider_message_id=CONCAT('SIMULATED-',id,'-',REPLACE(UUID(),'-','')),
                       sent_at=CURRENT_TIMESTAMP,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE mail_task_id=? AND status IN ('PENDING','FAILED')
                """, taskId);
        Integer success = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_log WHERE mail_task_id=? AND status='SUCCESS'", Integer.class, taskId);
        int successCount = success == null ? 0 : success;
        if (processed == 0 || successCount != totalCount) {
            throw new DepartmentBusinessException(500, "邮件日志状态更新不完整，任务已回滚");
        }
        jdbcTemplate.update("UPDATE mail_task SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?", taskId);
        jdbcTemplate.update("UPDATE report SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP WHERE id=?", task.reportId());
        jdbcTemplate.update("UPDATE review_task SET status='ARCHIVED',completed_at=COALESCE(completed_at,CURRENT_TIMESTAMP) " +
                        "WHERE report_id=? AND version_id=? AND review_stage='FINAL'",
                task.reportId(), task.versionId());
        return new SendResult(taskId, "COMPLETED", totalCount, successCount, 0);
    }

    private ReportForMail findReport(long reportId, long versionId) {
        return jdbcTemplate.query(
                "SELECT r.id report_id,v.id version_id FROM report r JOIN report_version v ON v.report_id=r.id WHERE r.id=? AND v.id=?",
                (rs, rowNum) -> new ReportForMail(rs.getLong("report_id"), rs.getLong("version_id")), reportId, versionId)
                .stream().findFirst().orElseThrow(() -> new DepartmentBusinessException(404, "报告或报告版本不存在"));
    }

    public record CreateRequest(long reportId, long versionId, String subject, String mailBody, List<Recipient> recipients) {}
    public record Recipient(String name, String email) {}
    public record RecipientOption(long id, String name, String email) {}
    public record CreateResult(long id, String status, int recipientCount) {}
    public record SendResult(long mailTaskId, String status, int totalCount, int successCount, int failedCount) {}
    private record ReportForMail(long reportId, long versionId) {}
    private record MailTask(long id, long reportId, long versionId, String subject, String body,
                            String status, long createdBy) {}
}
