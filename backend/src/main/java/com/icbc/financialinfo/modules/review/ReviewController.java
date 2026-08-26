package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplacementArticle;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SubmitReviewRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SubmitReviewResult;
import com.icbc.financialinfo.modules.review.ReviewIssueModels.CheckResult;
import com.icbc.financialinfo.modules.review.ReviewIssueModels.ReviewIssue;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ArticleSource;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.AssignedTaskSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.PageData;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportArticle;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewQueryRepository reviewRepository;
    private final ReviewCommandService commandService;
    private final ReviewIssueService issueService;

    public ReviewController(
            ReviewQueryRepository reviewRepository, ReviewCommandService commandService,
            ReviewIssueService issueService) {
        this.reviewRepository = reviewRepository;
        this.commandService = commandService;
        this.issueService = issueService;
    }

    @GetMapping("/reviews/pending")
    public ApiResponse<Map<String, Integer>> pending() {
        return ApiResponse.ok(Map.of("initial", 0, "final", 0));
    }

    @GetMapping("/review-tasks/my")
    public ApiResponse<PageData<AssignedTaskSummary>> myTasks(
            @RequestParam(defaultValue = "") String stage,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest servletRequest) {
        long reviewerId = operatorId(servletRequest);
        String role = String.valueOf(servletRequest.getAttribute("reviewRoleCode"));
        String assignedStage = "DEPT_MANAGER".equals(role) ? "FINAL" : "INITIAL";
        if (!stage.isBlank() && !assignedStage.equalsIgnoreCase(stage.trim())) {
            throw new ReviewOperationException(HttpStatus.FORBIDDEN, "无权查询其他审核阶段的任务");
        }
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        return new ApiResponse<>(200, "查询成功", new PageData<>(
                reviewRepository.countAssignedTasks(reviewerId, assignedStage, status),
                safePageNum, safePageSize,
                reviewRepository.findAssignedTasks(
                        reviewerId, assignedStage, status, safePageNum, safePageSize)));
    }

    @GetMapping("/report-versions/{versionId}/articles")
    public ResponseEntity<ApiResponse<List<ReportArticle>>> articles(@PathVariable long versionId) {
        if (!reviewRepository.versionExists(versionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "报告版本不存在", null));
        }
        return ResponseEntity.ok(new ApiResponse<>(
                200, "查询成功", reviewRepository.findArticles(versionId)));
    }

    @GetMapping("/report-articles/{articleId}/source")
    public ResponseEntity<ApiResponse<ArticleSource>> articleSource(@PathVariable long articleId) {
        return reviewRepository.findArticleSource(articleId)
                .map(source -> ResponseEntity.ok(new ApiResponse<>(200, "查询成功", source)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(404, "原始资讯不存在", null)));
    }

    @PutMapping("/review-tasks/{taskId}/articles/{articleId}")
    public ApiResponse<ModifyArticleResult> modifyArticle(
            @PathVariable long taskId, @PathVariable long articleId,
            @RequestBody ModifyArticleRequest request, HttpServletRequest servletRequest) {
        ModifyArticleResult result = commandService.modifyArticle(
                taskId, articleId, operatorId(servletRequest), request);
        return new ApiResponse<>(200, "报告条目修改成功", result);
    }

    @PostMapping("/review-tasks/{taskId}/comments")
    public ApiResponse<AddCommentResult> addComment(
            @PathVariable long taskId, @RequestBody AddCommentRequest request,
            HttpServletRequest servletRequest) {
        AddCommentResult result = commandService.addComment(
                taskId, operatorId(servletRequest), request);
        return new ApiResponse<>(200, "批注添加成功", result);
    }

    @PostMapping("/review-tasks/{taskId}/articles/{articleId}/replace")
    public ApiResponse<ReplaceArticleResult> replaceArticle(
            @PathVariable long taskId, @PathVariable long articleId,
            @RequestBody ReplaceArticleRequest request, HttpServletRequest servletRequest) {
        ReplaceArticleResult result = commandService.replaceArticle(
                taskId, articleId, operatorId(servletRequest), request);
        return new ApiResponse<>(200, "报告条目替换成功", result);
    }

    @GetMapping("/review-tasks/{taskId}/replacement-articles")
    public ApiResponse<List<ReplacementArticle>> replacementArticles(
            @PathVariable long taskId,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String keyword,
            HttpServletRequest servletRequest) {
        return new ApiResponse<>(200, "查询成功", commandService.replacementArticles(
                taskId, operatorId(servletRequest), category, keyword));
    }

    @PostMapping("/review-tasks/{taskId}/submit")
    public ApiResponse<SubmitReviewResult> submit(
            @PathVariable long taskId, @RequestBody SubmitReviewRequest request,
            HttpServletRequest servletRequest) {
        return new ApiResponse<>(200, "审核提交成功", commandService.submit(
                taskId, operatorId(servletRequest),
                String.valueOf(servletRequest.getAttribute("reviewRoleCode")), request));
    }

    @GetMapping("/report-versions/{versionId}/issues")
    public ApiResponse<List<ReviewIssue>> issues(@PathVariable long versionId) {
        return new ApiResponse<>(200, "查询成功", issueService.issues(versionId));
    }

    @PostMapping("/report-versions/{versionId}/check")
    public ApiResponse<CheckResult> check(@PathVariable long versionId) {
        return new ApiResponse<>(200, "审核检测完成", issueService.check(versionId));
    }

    @PutMapping("/review-issues/{id}/resolve")
    public ApiResponse<Void> resolveIssue(
            @PathVariable long id, HttpServletRequest servletRequest) {
        issueService.resolve(id, operatorId(servletRequest));
        return new ApiResponse<>(200, "审核问题已标记为已处理", null);
    }

    @ExceptionHandler(ReviewOperationException.class)
    public ResponseEntity<ApiResponse<Object>> reviewOperationError(
            ReviewOperationException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(
                        exception.status().value(), exception.getMessage(), exception.data()));
    }

    private long operatorId(HttpServletRequest request) {
        Object userId = request.getAttribute("reviewUserId");
        if (userId instanceof Long id) return id;
        throw new ReviewOperationException(HttpStatus.FORBIDDEN, "无权操作审核任务");
    }
}
