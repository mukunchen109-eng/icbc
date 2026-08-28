package com.icbc.financialinfo.modules.report.controller;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.report.AdminReportService;
import com.icbc.financialinfo.modules.report.AdminReportService.ManageRequest;
import com.icbc.financialinfo.modules.report.AdminReportService.ReviewerOption;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.service.ReportService;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.AdminReviewReportSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.PageData;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportReviewDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryRepository;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReviewQueryRepository reviewRepository;
    private final AdminReportService adminReportService;

    public ReportController(ReportService reportService,
                            ReviewQueryRepository reviewRepository,
                            AdminReportService adminReportService) {
        this.reportService = reportService;
        this.reviewRepository = reviewRepository;
        this.adminReportService = adminReportService;
    }

    @GetMapping
    public ApiResponse<List<ReportListItem>> list() {
        return ApiResponse.ok(reportService.listReports());
    }

    @GetMapping("/review")
    public ApiResponse<PageData<ReportSummary>> reviewReports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "") String reportDate,
            @RequestParam(defaultValue = "") String status) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        return new ApiResponse<>(200, "查询成功", new PageData<>(
                reviewRepository.countReviewReports(reportDate, status),
                safePageNum, safePageSize,
                reviewRepository.findReviewPage(safePageNum, safePageSize, reportDate, status)));
    }

    @GetMapping("/{id}/review-detail")
    public ResponseEntity<ApiResponse<ReportReviewDetail>> reviewDetail(@PathVariable long id) {
        return reviewRepository.findReport(id)
                .map(report -> ResponseEntity.ok(new ApiResponse<>(200, "查询成功",
                        new ReportReviewDetail(report.id(), report.reportDate(), report.reportTitle(),
                                report.status(), report.locked(), report.lockedBy(), report.lockedAt(),
                                report.createdAt(), report.updatedAt(),
                                reviewRepository.findLatestDepartmentComment(id)))))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(new ApiResponse<>(404, "报告不存在", null)));
    }

    @GetMapping("/admin/review")
    public ApiResponse<PageData<AdminReviewReportSummary>> adminReviewReports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        return new ApiResponse<>(200, "查询成功", new PageData<>(
                reviewRepository.countAllReports(), safePageNum, safePageSize,
                reviewRepository.findAdminReviewPage(safePageNum, safePageSize)));
    }

    @GetMapping("/admin/reviewers")
    public ApiResponse<List<ReviewerOption>> reviewers(@RequestParam String stage) {
        return ApiResponse.ok(adminReportService.reviewers(stage));
    }

    @PutMapping("/admin/{id}/review-management")
    public ApiResponse<Void> manageReview(
            @PathVariable long id, @RequestBody ManageRequest request) {
        adminReportService.manage(id, request == null ? null : request.stage(),
                request == null ? null : request.reviewerId());
        return new ApiResponse<>(200, "报告审核信息修改成功", null);
    }

    @GetMapping("/admin/{id}/archive/pdf")
    public ResponseEntity<Resource> archivedPdf(@PathVariable long id) {
        Path path = adminReportService.archivedPdf(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<GeneratedReportResponse> detail(@PathVariable String reportId) {
        return ApiResponse.ok(reportService.getReport(reportId));
    }

    @PostMapping("/daily-summary")
    public ApiResponse<GeneratedReportResponse> generateDailySummary(@RequestBody @Valid GenerateDailySummaryRequest request) {
        return ApiResponse.ok(reportService.generateDailySummary(request));
    }

    @ExceptionHandler(DepartmentBusinessException.class)
    public ResponseEntity<ApiResponse<Object>> adminError(DepartmentBusinessException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(exception.status(), exception.getMessage(), null));
    }
}
