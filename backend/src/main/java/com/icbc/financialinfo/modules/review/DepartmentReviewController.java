package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.user.UserRepository.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/review-tasks")
public class DepartmentReviewController {
    private final JdbcTemplate jdbcTemplate;
    private final DepartmentAuthorizationService authorizationService;

    public DepartmentReviewController(JdbcTemplate jdbcTemplate,
                                      DepartmentAuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageData<ReviewTaskItem>>> myTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "FINAL") String stage,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        try {
            UserAccount manager = authorizationService.requireDepartmentManager(authorization);
            if (!"FINAL".equalsIgnoreCase(stage)) {
                throw new DepartmentBusinessException(403, "部室负责人只能查看终审任务");
            }
            int safePage = Math.max(1, pageNum);
            int safeSize = Math.min(100, Math.max(1, pageSize));
            List<Object> parameters = new ArrayList<>(List.of(manager.id()));
            String statusClause = "";
            if (status != null && !status.isBlank()) {
                statusClause = " AND t.status = ?";
                parameters.add(status.trim().toUpperCase());
            }
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM review_task t WHERE t.review_stage='FINAL' AND t.reviewer_id=?" + statusClause,
                    Long.class, parameters.toArray());
            parameters.add(safeSize);
            parameters.add((safePage - 1) * safeSize);
            List<ReviewTaskItem> records = jdbcTemplate.query("""
                    SELECT t.id,t.report_id,t.version_id,t.review_stage,t.status,
                           DATE_FORMAT(t.submitted_at,'%Y-%m-%d %H:%i:%s') submitted_at,
                           DATE_FORMAT(t.completed_at,'%Y-%m-%d %H:%i:%s') completed_at,
                           r.report_title,DATE_FORMAT(r.report_date,'%Y-%m-%d') report_date,
                           v.version_no,v.word_file_path,v.pdf_file_path
                      FROM review_task t
                      JOIN report r ON r.id=t.report_id
                      JOIN report_version v ON v.id=t.version_id
                     WHERE t.review_stage='FINAL' AND t.reviewer_id=?
                    """ + statusClause + " ORDER BY t.submitted_at DESC LIMIT ? OFFSET ?",
                    (rs, rowNum) -> new ReviewTaskItem(
                            rs.getLong("id"), rs.getLong("report_id"), rs.getLong("version_id"),
                            rs.getInt("version_no"), rs.getString("review_stage"), rs.getString("status"),
                            rs.getString("report_date"), rs.getString("report_title"),
                            rs.getString("submitted_at"), rs.getString("completed_at"),
                            rs.getString("word_file_path"), rs.getString("pdf_file_path")),
                    parameters.toArray());
            PageData<ReviewTaskItem> data = new PageData<>(total == null ? 0 : total, safePage, safeSize, records);
            return ResponseEntity.ok(new ApiResponse<>(200, "查询成功", data));
        } catch (DepartmentBusinessException exception) {
            return ResponseEntity.status(exception.status())
                    .body(new ApiResponse<>(exception.status(), exception.getMessage(), null));
        }
    }

    public record PageData<T>(long total, int pageNum, int pageSize, List<T> records) {}
    public record ReviewTaskItem(long id, long reportId, long versionId, int versionNo,
                                 String reviewStage, String status, String reportDate,
                                 String reportTitle, String submittedAt, String completedAt,
                                 String wordFilePath, String pdfFilePath) {}
}
