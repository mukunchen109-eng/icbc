package com.icbc.financialinfo.modules.report;

import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class AdminReportService {
    private final JdbcTemplate jdbc;
    private final Path archiveRoot;

    public AdminReportService(JdbcTemplate jdbc, @Value("${app.archive.root:./data/archives}") String archiveRoot) {
        this.jdbc = jdbc;
        this.archiveRoot = Path.of(archiveRoot).toAbsolutePath().normalize();
    }

    public List<ReviewerOption> reviewers(String stage) {
        String role = roleForStage(stage);
        return jdbc.query("SELECT u.id,u.username FROM sys_user u JOIN sys_role r ON r.id=u.role_id WHERE u.status=1 AND r.role_code=? ORDER BY u.username",
                (rs, row) -> new ReviewerOption(rs.getLong("id"), rs.getString("username")), role);
    }

    @Transactional
    public void manage(long reportId, String stage, Long reviewerId) {
        String normalizedStage = normalizeStage(stage);
        String role = roleForStage(normalizedStage);
        if (reviewerId == null) throw new DepartmentBusinessException(400, "请选择审核人员");
        String status = jdbc.query("SELECT status FROM report WHERE id=? FOR UPDATE", (rs, row) -> rs.getString(1), reportId)
                .stream().findFirst().orElseThrow(() -> new DepartmentBusinessException(404, "报告不存在"));
        if ("FINAL_ARCHIVED".equals(status)) throw new DepartmentBusinessException(409, "已归档报告不能修改");
        if ("FINAL_APPROVED".equals(status) && !"FINAL".equals(normalizedStage)) {
            throw new DepartmentBusinessException(400, "终审已通过的报告只能退回终审");
        }
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u JOIN sys_role r ON r.id=u.role_id WHERE u.id=? AND u.status=1 AND r.role_code=?",
                Integer.class, reviewerId, role);
        if (valid == null || valid == 0) throw new DepartmentBusinessException(400, "所选人员与审核阶段不匹配或账号已停用");
        jdbc.update("UPDATE review_task SET completed_at=CURRENT_TIMESTAMP WHERE report_id=? AND completed_at IS NULL", reportId);
        Long existingTaskId = jdbc.query("SELECT id FROM review_task WHERE report_id=? AND reviewer_id=? ORDER BY id DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), reportId, reviewerId).stream().findFirst().orElse(null);
        if (existingTaskId != null) {
            jdbc.update("UPDATE review_task SET review_comment=NULL,submitted_at=CURRENT_TIMESTAMP,completed_at=NULL WHERE id=?", existingTaskId);
        } else {
            jdbc.update("INSERT INTO review_task(report_id,reviewer_id,review_comment,submitted_at,completed_at) VALUES(?,?,NULL,CURRENT_TIMESTAMP,NULL)", reportId, reviewerId);
        }
        jdbc.update("UPDATE report SET status=?,locked=0,locked_by=NULL,locked_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                normalizedStage + "_PENDING", reportId);
    }

    public Path archivedPdf(long reportId) {
        String value = jdbc.query("SELECT ar.final_report_path FROM archive_record ar JOIN report r ON r.id=ar.report_id WHERE ar.report_id=? AND r.status='FINAL_ARCHIVED'",
                (rs, row) -> rs.getString(1), reportId).stream().findFirst()
                .orElseThrow(() -> new DepartmentBusinessException(404, "未找到该报告的归档PDF"));
        Path pdf = Path.of(value).toAbsolutePath().normalize();
        if (!pdf.startsWith(archiveRoot) || !Files.isRegularFile(pdf) || !pdf.getFileName().toString().toLowerCase().endsWith(".pdf")) {
            throw new DepartmentBusinessException(404, "归档PDF不存在或存储位置无效");
        }
        return pdf;
    }

    private String normalizeStage(String stage) {
        String value = stage == null ? "" : stage.trim().toUpperCase();
        if (!List.of("INITIAL", "FINAL").contains(value)) throw new DepartmentBusinessException(400, "审核阶段必须为初审或终审");
        return value;
    }
    private String roleForStage(String stage) { return "FINAL".equals(normalizeStage(stage)) ? "DEPT_MANAGER" : "INFO_MANAGER"; }

    public record ReviewerOption(Long id, String username) {}
    public record ManageRequest(String stage, Long reviewerId) {}
}
