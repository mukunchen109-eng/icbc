package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mail-logs")
public class AdminMailLogController {
    private final JdbcTemplate jdbc;

    public AdminMailLogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<PageData<MailDeliveryRecord>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM mail_log WHERE mail_status IN ('SUCCESS','FAILED')", Long.class);
        List<MailDeliveryRecord> records = jdbc.query("""
                SELECT ml.id,ml.recipient_name,ml.recipient_email,mt.subject,ml.mail_status,
                       DATE_FORMAT(ml.sent_at,'%Y-%m-%d %H:%i:%s') sent_at
                  FROM mail_log ml
                  JOIN mail_task mt ON mt.id=ml.mail_task_id
                 WHERE ml.mail_status IN ('SUCCESS','FAILED')
                 ORDER BY COALESCE(ml.sent_at,ml.updated_at,ml.created_at) DESC,ml.id DESC
                 LIMIT ? OFFSET ?
                """, (rs, row) -> new MailDeliveryRecord(
                rs.getLong("id"), rs.getString("recipient_name"), rs.getString("recipient_email"),
                rs.getString("subject"), rs.getString("mail_status"), rs.getString("sent_at")),
                safePageSize, (safePageNum - 1) * safePageSize);
        return new ApiResponse<>(200, "查询成功",
                new PageData<>(count == null ? 0 : count, safePageNum, safePageSize, records));
    }

    public record PageData<T>(long total, int pageNum, int pageSize, List<T> records) {}
    public record MailDeliveryRecord(Long id, String recipientName, String recipientEmail,
                                     String subject, String mailStatus, String sentAt) {}
}
