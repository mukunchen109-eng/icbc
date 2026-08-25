package com.icbc.financialinfo.modules.report;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.PageData;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportReviewDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReviewTaskDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.VersionDetail;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.VersionSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReviewQueryRepository reviewRepository;

    public ReportController(ReviewQueryRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.ok(Map.of("items", List.of(), "total", 0));
    }

    @GetMapping("/review")
    public ApiResponse<PageData<ReportSummary>> reviewReports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "") String reportDate,
            @RequestParam(defaultValue = "") String status) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        List<ReportSummary> records = reviewRepository.findReviewPage(
                safePageNum, safePageSize, reportDate, status);
        long total = reviewRepository.countReviewReports(reportDate, status);
        return new ApiResponse<>(200, "查询成功",
                new PageData<>(total, safePageNum, safePageSize, records));
    }

    @GetMapping("/{id}/review-detail")
    public ResponseEntity<ApiResponse<ReportReviewDetail>> reviewDetail(@PathVariable long id) {
        ReportSummary report = reviewRepository.findReport(id).orElse(null);
        if (report == null) return reportNotFound();
        // 根据报告的当前版本号查询版本
        VersionDetail version = reviewRepository.findVersion(id, report.currentVersionNo()).orElse(null);
        // 根据版本号查询当前审核任务
        ReviewTaskDetail task = version == null ? null
                : reviewRepository.findCurrentReviewTask(id, version.id()).orElse(null);
        ReportReviewDetail detail = new ReportReviewDetail(
                report.id(), report.reportDate(), report.reportTitle(), report.status(),
                report.currentVersionNo(), report.locked(), report.lockedBy(), report.lockedAt(),
                version, task);
        return ResponseEntity.ok(new ApiResponse<>(200, "查询成功", detail));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<VersionSummary>>> versions(@PathVariable long id) {
        if (reviewRepository.findReport(id).isEmpty()) return reportNotFound();
        return ResponseEntity.ok(new ApiResponse<>(
                200, "查询成功", reviewRepository.findVersions(id)));
    }

    private <T> ResponseEntity<ApiResponse<T>> reportNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "报告不存在", null));
    }
}
