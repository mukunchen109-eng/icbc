package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.modules.archive.ReportArchiveService;
import com.icbc.financialinfo.modules.archive.ReportArchiveService.AttachmentInfo;
import com.icbc.financialinfo.modules.archive.ReportArchiveService.PreparedArtifacts;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentMailService {

  private static final Set<String> SENDABLE_STATUSES = Set.of(
    "PENDING",
    "FAILED",
    "PARTIAL_FAILED"
  );
  private final JdbcTemplate jdbc;
  private final ReportArchiveService archiveService;
  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String fromAddress;
  private final String fromName;

  public DepartmentMailService(
    JdbcTemplate jdbc,
    ReportArchiveService archiveService,
    ObjectProvider<JavaMailSender> mailSenderProvider,
    @Value("${app.mail.enabled:false}") boolean mailEnabled,
    @Value("${app.mail.from:}") String fromAddress,
    @Value("${app.mail.from-name:金融智讯}") String fromName
  ) {
    this.jdbc = jdbc;
    this.archiveService = archiveService;
    this.mailSender = mailSenderProvider.getIfAvailable();
    this.mailEnabled = mailEnabled;
    this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    this.fromName = fromName;
  }

  public List<RecipientOption> listRecipients() {
    return jdbc.query(
      "SELECT id,recipient_name,recipient_email FROM mail_recipient ORDER BY id",
      (rs, row) -> new RecipientOption(rs.getLong(1), rs.getString(2), rs.getString(3))
    );
  }

  public RecipientOption addRecipient(long user, String name, String email) {
    String normalizedName = name.trim();
    String normalizedEmail = email.trim().toLowerCase();
    KeyHolder keys = new GeneratedKeyHolder();
    try {
      jdbc.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO mail_recipient(recipient_name,recipient_email,created_by,created_at,updated_at)
            VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,
            Statement.RETURN_GENERATED_KEYS
          );
          statement.setString(1, normalizedName);
          statement.setString(2, normalizedEmail);
          statement.setLong(3, user);
          return statement;
        },
        keys
      );
    } catch (DuplicateKeyException exception) {
      throw new DepartmentBusinessException(409, "该收件邮箱已经存在");
    }
    if (keys.getKey() == null) throw new DepartmentBusinessException(500, "数据库未返回收件人编号");
    return new RecipientOption(keys.getKey().longValue(), normalizedName, normalizedEmail);
  }

  @Transactional
  public CreateResult create(long manager, CreateRequest request) {
    String reportStatus = jdbc
      .query(
        "SELECT status FROM report WHERE id=?",
        (rs, row) -> rs.getString(1),
        request.reportId()
      )
      .stream()
      .findFirst()
      .orElseThrow(() -> new DepartmentBusinessException(404, "报告不存在"));
    if (!"FINAL_APPROVED".equals(reportStatus)) throw new DepartmentBusinessException(
      409,
      "报告尚未通过终审，不能发送"
    );

    for (Recipient recipient : request.recipients()) {
      Integer count = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM mail_recipient
         WHERE recipient_name=? AND LOWER(recipient_email)=LOWER(?)
        """,
        Integer.class,
        recipient.name().trim(),
        recipient.email().trim()
      );
      if (count == null || count == 0) throw new DepartmentBusinessException(
        400,
        "收件人不存在：" + recipient.email()
      );
    }

    Integer sent = jdbc.queryForObject(
      """
      SELECT COUNT(*) FROM mail_task
       WHERE report_id=? AND mail_status='SENDING'
      """,
      Integer.class,
      request.reportId()
    );
    if (sent != null && sent > 0) throw new DepartmentBusinessException(
      409,
      "该报告正在发送，请勿重复操作"
    );

    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
      connection -> {
        PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO mail_task(report_id,subject,mail_body,mail_status,created_by,created_at)
          VALUES(?,?,?,'PENDING',?,CURRENT_TIMESTAMP)
          """,
          Statement.RETURN_GENERATED_KEYS
        );
        statement.setLong(1, request.reportId());
        statement.setString(2, request.subject().trim());
        statement.setString(3, request.mailBody());
        statement.setLong(4, manager);
        return statement;
      },
      keys
    );
    if (keys.getKey() == null) throw new DepartmentBusinessException(
      500,
      "数据库未返回邮件任务编号"
    );
    long taskId = keys.getKey().longValue();
    for (Recipient recipient : request.recipients()) {
      jdbc.update(
        """
        INSERT INTO mail_log(mail_task_id,recipient_name,recipient_email,mail_status,retry_count,created_at,updated_at)
        VALUES(?,?,?,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """,
        taskId,
        recipient.name().trim(),
        recipient.email().trim().toLowerCase()
      );
    }
    return new CreateResult(taskId, "PENDING", request.recipients().size());
  }

  public SendResult send(long manager, long taskId) {
    MailTask task = task(manager, taskId);
    if (!SENDABLE_STATUSES.contains(task.status())) throw new DepartmentBusinessException(
      409,
      "邮件任务已处理"
    );

    PreparedArtifacts artifacts = archiveService.prepare(task.reportId());
    List<MailRecipientLog> pending = recipientLogs(taskId)
      .stream()
      .filter(log -> !"SUCCESS".equals(log.status()))
      .toList();
    if (pending.isEmpty() && recipientLogs(taskId).isEmpty()) throw new DepartmentBusinessException(
      409,
      "邮件任务没有收件人"
    );

    jdbc.update("UPDATE mail_task SET mail_status='SENDING',completed_at=NULL WHERE id=?", taskId);
    for (MailRecipientLog log : pending) deliver(task, log, artifacts, false);
    return completeTask(task, manager, artifacts);
  }

  public MailLogDetail retry(long manager, long logId) {
    MailRecipientLog log = recipientLog(logId);
    MailTask task = task(manager, log.taskId());
    if (!"FAILED".equals(log.status())) throw new DepartmentBusinessException(
      409,
      "只有失败记录可以重试"
    );
    PreparedArtifacts artifacts = archiveService.prepare(task.reportId());
    jdbc.update(
      "UPDATE mail_task SET mail_status='SENDING',completed_at=NULL WHERE id=?",
      task.id()
    );
    deliver(task, log, artifacts, true);
    completeTask(task, manager, artifacts);
    return logs(manager, task.id())
      .stream()
      .filter(item -> item.id() == logId)
      .findFirst()
      .orElseThrow();
  }

  private void deliver(
    MailTask task,
    MailRecipientLog log,
    PreparedArtifacts artifacts,
    boolean retry
  ) {
    try {
      if (!mailEnabled) throw new IllegalStateException(
        "真实邮件发送未启用，请设置 MAIL_ENABLED=true"
      );
      if (mailSender == null || fromAddress.isBlank()) throw new IllegalStateException(
        "发件邮箱未配置，请设置 MAIL_HOST、MAIL_USERNAME、MAIL_PASSWORD 和 MAIL_FROM"
      );
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
        message,
        true,
        StandardCharsets.UTF_8.name()
      );
      helper.setFrom(fromAddress, fromName);
      helper.setTo(log.email());
      helper.setSubject(task.subject());
      helper.setText(task.body() == null ? "" : task.body(), false);
      helper.addAttachment(artifacts.pdf().getFileName().toString(), artifacts.pdf().toFile());
      helper.addAttachment(artifacts.docx().getFileName().toString(), artifacts.docx().toFile());
      mailSender.send(message);
      String messageId = message.getMessageID();
      if (messageId == null || messageId.isBlank()) messageId = "SMTP-" + UUID.randomUUID();
      jdbc.update(
        """
        UPDATE mail_log SET mail_status='SUCCESS',retry_count=retry_count+?,error_message=NULL,
               provider_message_id=?,sent_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
         WHERE id=?
        """,
        retry ? 1 : 0,
        messageId,
        log.id()
      );
    } catch (Exception exception) {
      jdbc.update(
        """
        UPDATE mail_log SET mail_status='FAILED',retry_count=retry_count+?,error_message=?,
               provider_message_id=NULL,updated_at=CURRENT_TIMESTAMP
         WHERE id=?
        """,
        retry ? 1 : 0,
        truncate(message(exception), 2000),
        log.id()
      );
    }
  }

  private SendResult completeTask(MailTask task, long manager, PreparedArtifacts artifacts) {
    List<MailLogDetail> results = logs(manager, task.id());
    int total = results.size();
    int success = (int) results
      .stream()
      .filter(log -> "SUCCESS".equals(log.status()))
      .count();
    int failed = total - success;
    String status = success == total && total > 0
      ? "COMPLETED"
      : success == 0
        ? "FAILED"
        : "PARTIAL_FAILED";
    try {
      archiveService.archive(task.reportId(), manager, artifacts);
    } catch (RuntimeException exception) {
      jdbc.update(
        "UPDATE mail_task SET mail_status='PARTIAL_FAILED',completed_at=NULL WHERE id=?",
        task.id()
      );
      throw exception;
    }
    if ("COMPLETED".equals(status)) {
      jdbc.update(
        "UPDATE mail_task SET mail_status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?",
        task.id()
      );
      jdbc.update(
        """
        UPDATE report SET status='FINAL_ARCHIVED',updated_at=CURRENT_TIMESTAMP
         WHERE id=? AND status='FINAL_APPROVED'
        """,
        task.reportId()
      );
    } else {
      jdbc.update(
        "UPDATE mail_task SET mail_status=?,completed_at=NULL WHERE id=?",
        status,
        task.id()
      );
    }
    return new SendResult(
      task.id(),
      status,
      total,
      success,
      failed,
      artifacts.attachments(),
      logs(manager, task.id())
    );
  }

  public MailTaskDetail detail(long manager, long id) {
    MailTask task = task(manager, id);
    return jdbc
      .query(
        """
        SELECT id,report_id,subject,mail_body,mail_status,created_by,
               DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') created_at,
               DATE_FORMAT(completed_at,'%Y-%m-%d %H:%i:%s') completed_at
          FROM mail_task WHERE id=?
        """,
        (rs, row) ->
          new MailTaskDetail(
            rs.getLong("id"),
            rs.getLong("report_id"),
            rs.getString("subject"),
            rs.getString("mail_body"),
            rs.getString("mail_status"),
            rs.getLong("created_by"),
            rs.getString("created_at"),
            rs.getString("completed_at")
          ),
        task.id()
      )
      .get(0);
  }

  public List<MailLogDetail> logs(long manager, long taskId) {
    task(manager, taskId);
    return jdbc.query(
      """
      SELECT id,mail_task_id,recipient_name,recipient_email,mail_status,retry_count,error_message,
             provider_message_id,DATE_FORMAT(sent_at,'%Y-%m-%d %H:%i:%s') sent_at,
             DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') created_at,
             DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') updated_at
        FROM mail_log WHERE mail_task_id=? ORDER BY id
      """,
      (rs, row) ->
        new MailLogDetail(
          rs.getLong("id"),
          rs.getLong("mail_task_id"),
          rs.getString("recipient_name"),
          rs.getString("recipient_email"),
          rs.getString("mail_status"),
          rs.getInt("retry_count"),
          rs.getString("error_message"),
          rs.getString("provider_message_id"),
          rs.getString("sent_at"),
          rs.getString("created_at"),
          rs.getString("updated_at")
        ),
      taskId
    );
  }

  private MailTask task(long manager, long id) {
    MailTask task = jdbc
      .query(
        """
        SELECT id,report_id,subject,mail_body,mail_status,created_by
          FROM mail_task WHERE id=?
        """,
        (rs, row) ->
          new MailTask(
            rs.getLong("id"),
            rs.getLong("report_id"),
            rs.getString("subject"),
            rs.getString("mail_body"),
            rs.getString("mail_status"),
            rs.getLong("created_by")
          ),
        id
      )
      .stream()
      .findFirst()
      .orElseThrow(() -> new DepartmentBusinessException(404, "邮件任务不存在"));
    if (task.createdBy() != manager) throw new DepartmentBusinessException(
      403,
      "无权操作其他用户创建的邮件任务"
    );
    return task;
  }

  private List<MailRecipientLog> recipientLogs(long taskId) {
    return jdbc.query(
      """
      SELECT id,mail_task_id,recipient_name,recipient_email,mail_status
        FROM mail_log WHERE mail_task_id=? ORDER BY id
      """,
      (rs, row) ->
        new MailRecipientLog(
          rs.getLong("id"),
          rs.getLong("mail_task_id"),
          rs.getString("recipient_name"),
          rs.getString("recipient_email"),
          rs.getString("mail_status")
        ),
      taskId
    );
  }

  private MailRecipientLog recipientLog(long id) {
    return jdbc
      .query(
        """
        SELECT id,mail_task_id,recipient_name,recipient_email,mail_status
          FROM mail_log WHERE id=?
        """,
        (rs, row) ->
          new MailRecipientLog(
            rs.getLong("id"),
            rs.getLong("mail_task_id"),
            rs.getString("recipient_name"),
            rs.getString("recipient_email"),
            rs.getString("mail_status")
          ),
        id
      )
      .stream()
      .findFirst()
      .orElseThrow(() -> new DepartmentBusinessException(404, "邮件日志不存在"));
  }

  private String message(Exception exception) {
    Throwable root = exception;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String message = root.getMessage();
    return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
  }

  private String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  public record CreateRequest(
    long reportId,
    String subject,
    String mailBody,
    List<Recipient> recipients
  ) {}

  public record Recipient(String name, String email) {}

  public record RecipientOption(long id, String name, String email) {}

  public record CreateResult(long id, String status, int recipientCount) {}

  public record SendResult(
    long mailTaskId,
    String status,
    int totalCount,
    int successCount,
    int failedCount,
    List<AttachmentInfo> attachments,
    List<MailLogDetail> logs
  ) {}

  public record MailTaskDetail(
    long id,
    long reportId,
    String subject,
    String mailBody,
    String status,
    long createdBy,
    String createdAt,
    String completedAt
  ) {}

  public record MailLogDetail(
    long id,
    long mailTaskId,
    String recipientName,
    String recipientEmail,
    String status,
    int retryCount,
    String errorMessage,
    String providerMessageId,
    String sentAt,
    String createdAt,
    String updatedAt
  ) {}

  private record MailTask(
    long id,
    long reportId,
    String subject,
    String body,
    String status,
    long createdBy
  ) {}

  private record MailRecipientLog(long id, long taskId, String name, String email, String status) {}
}
