package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddMarkRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddMarkResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplacementArticle;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SaveDraftRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SaveDraftResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SubmitReviewRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.SubmitReviewResult;
import com.icbc.financialinfo.modules.review.ReviewIssueModels.CheckResult;
import com.icbc.financialinfo.modules.review.ReviewIssueModels.ReviewIssue;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ArticleSource;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.AssignedTaskSummary;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.PageData;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReportArticle;
import com.icbc.financialinfo.modules.review.ReviewQueryModels.ReviewRecordView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
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

@RestController
@RequestMapping("/api")
public class ReviewController {

  private final ReviewQueryRepository reviewRepository;
  private final ReviewCommandService commandService;
  private final ReviewIssueService issueService;
  private final ReviewDraftService draftService;

  public ReviewController(
    ReviewQueryRepository reviewRepository,
    ReviewCommandService commandService,
    ReviewIssueService issueService,
    ReviewDraftService draftService
  ) {
    this.reviewRepository = reviewRepository;
    this.commandService = commandService;
    this.issueService = issueService;
    this.draftService = draftService;
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
    HttpServletRequest servletRequest
  ) {
    long reviewerId = operatorId(servletRequest);
    String role = String.valueOf(servletRequest.getAttribute("reviewRoleCode"));
    String assignedStage = "DEPT_MANAGER".equals(role) ? "FINAL" : "INITIAL";
    if (!stage.isBlank() && !assignedStage.equalsIgnoreCase(stage.trim())) {
      throw new ReviewOperationException(HttpStatus.FORBIDDEN, "无权查询其他审核阶段的任务");
    }
    int safePageNum = Math.max(pageNum, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    reviewRepository.ensureMissingTasks();
    return new ApiResponse<>(
      200,
      "查询成功",
      new PageData<>(
        reviewRepository.countAssignedTasks(reviewerId, assignedStage, status),
        safePageNum,
        safePageSize,
        reviewRepository.findAssignedTasks(
          reviewerId,
          assignedStage,
          status,
          safePageNum,
          safePageSize
        )
      )
    );
  }

  @GetMapping("/review-tasks/{id}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> taskDetail(
    @PathVariable long id,
    HttpServletRequest servletRequest
  ) {
    return reviewRepository
      .findTaskDetail(id, operatorId(servletRequest))
      .map(task -> ResponseEntity.ok(new ApiResponse<>(200, "查询成功", task)))
      .orElseGet(() ->
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
          new ApiResponse<>(404, "审核任务不存在", null)
        )
      );
  }

  @GetMapping("/reports/{reportId}/articles")
  public ResponseEntity<ApiResponse<List<ReportArticle>>> articles(@PathVariable long reportId) {
    if (!reviewRepository.reportExists(reportId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        new ApiResponse<>(404, "报告不存在", null)
      );
    }
    return ResponseEntity.ok(
      new ApiResponse<>(200, "查询成功", reviewRepository.findArticles(reportId))
    );
  }

  @GetMapping("/report-articles/{articleId}/source")
  public ResponseEntity<ApiResponse<ArticleSource>> articleSource(@PathVariable long articleId) {
    return reviewRepository
      .findArticleSource(articleId)
      .map(source -> ResponseEntity.ok(new ApiResponse<>(200, "查询成功", source)))
      .orElseGet(() ->
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
          new ApiResponse<>(404, "原始资讯不存在", null)
        )
      );
  }

  @GetMapping("/reports/{reportId}/sources")
  public ResponseEntity<ApiResponse<List<ArticleSource>>> reportSources(
    @PathVariable long reportId
  ) {
    if (!reviewRepository.reportExists(reportId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        new ApiResponse<>(404, "报告不存在", null)
      );
    }
    return ResponseEntity.ok(
      new ApiResponse<>(200, "查询成功", reviewRepository.findReportSources(reportId))
    );
  }

  @PutMapping("/review-tasks/{taskId}/articles/{articleId}")
  public ApiResponse<ModifyArticleResult> modifyArticle(
    @PathVariable long taskId,
    @PathVariable long articleId,
    @RequestBody ModifyArticleRequest request,
    HttpServletRequest servletRequest
  ) {
    ModifyArticleResult result = commandService.modifyArticle(
      reportId(taskId, servletRequest),
      articleId,
      operatorId(servletRequest),
      role(servletRequest),
      request
    );
    return new ApiResponse<>(200, "报告条目修改成功", result);
  }

  @PostMapping("/review-tasks/{taskId}/comments")
  public ApiResponse<AddCommentResult> addComment(
    @PathVariable long taskId,
    @RequestBody AddCommentRequest request,
    HttpServletRequest servletRequest
  ) {
    AddCommentResult result = commandService.addComment(
      reportId(taskId, servletRequest),
      operatorId(servletRequest),
      role(servletRequest),
      request
    );
    return new ApiResponse<>(200, "批注添加成功", result);
  }

  @PostMapping("/review-tasks/{taskId}/articles/{articleId}/replace")
  public ApiResponse<ReplaceArticleResult> replaceArticle(
    @PathVariable long taskId,
    @PathVariable long articleId,
    @RequestBody ReplaceArticleRequest request,
    HttpServletRequest servletRequest
  ) {
    ReplaceArticleResult result = commandService.replaceArticle(
      reportId(taskId, servletRequest),
      articleId,
      operatorId(servletRequest),
      role(servletRequest),
      request
    );
    return new ApiResponse<>(200, "报告条目替换成功", result);
  }

  @GetMapping("/review-tasks/{taskId}/replacement-articles")
  public ApiResponse<List<ReplacementArticle>> replacementArticles(
    @PathVariable long taskId,
    @RequestParam(defaultValue = "") String category,
    @RequestParam(defaultValue = "") String keyword,
    HttpServletRequest servletRequest
  ) {
    return new ApiResponse<>(
      200,
      "查询成功",
      commandService.replacementArticles(
        reportId(taskId, servletRequest),
        operatorId(servletRequest),
        role(servletRequest),
        category,
        keyword
      )
    );
  }

  @PostMapping("/review-tasks/{taskId}/marks")
  public ApiResponse<AddMarkResult> addMark(
    @PathVariable long taskId,
    @RequestBody AddMarkRequest request,
    HttpServletRequest servletRequest
  ) {
    return new ApiResponse<>(
      200,
      "审核标记保存成功",
      commandService.addMark(
        reportId(taskId, servletRequest),
        operatorId(servletRequest),
        role(servletRequest),
        request
      )
    );
  }

  @PostMapping("/review-tasks/{taskId}/draft")
  public ApiResponse<SaveDraftResult> saveDraft(
    @PathVariable long taskId,
    @RequestBody SaveDraftRequest request,
    HttpServletRequest servletRequest
  ) {
    return new ApiResponse<>(
      200,
      "草稿保存成功",
      draftService.save(
        reportId(taskId, servletRequest),
        operatorId(servletRequest),
        role(servletRequest),
        request
      )
    );
  }

  @GetMapping("/review-tasks/{taskId}/records")
  public ApiResponse<List<ReviewRecordView>> reviewRecords(
    @PathVariable long taskId,
    HttpServletRequest servletRequest
  ) {
    return new ApiResponse<>(
      200,
      "查询成功",
      commandService.reviewRecords(
        reportId(taskId, servletRequest),
        operatorId(servletRequest),
        role(servletRequest)
      )
    );
  }

  @GetMapping("/reports/{reportId}/review-records")
  public ApiResponse<PageData<ReviewRecordView>> reportReviewRecords(
    @PathVariable long reportId,
    @RequestParam(defaultValue = "1") int pageNum,
    @RequestParam(defaultValue = "20") int pageSize,
    HttpServletRequest servletRequest
  ) {
    List<ReviewRecordView> records = commandService.reviewRecords(
      reportId,
      operatorId(servletRequest),
      role(servletRequest)
    );
    int safePageNum = Math.max(pageNum, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    int from = Math.min((safePageNum - 1) * safePageSize, records.size());
    int to = Math.min(from + safePageSize, records.size());
    return new ApiResponse<>(
      200,
      "查询成功",
      new PageData<>(records.size(), safePageNum, safePageSize, records.subList(from, to))
    );
  }

  @PostMapping("/review-tasks/{taskId}/submit")
  public ApiResponse<SubmitReviewResult> submit(
    @PathVariable long taskId,
    @RequestBody SubmitReviewRequest request,
    HttpServletRequest servletRequest
  ) {
    return new ApiResponse<>(
      200,
      "审核提交成功",
      commandService.submit(
        reportId(taskId, servletRequest),
        operatorId(servletRequest),
        role(servletRequest),
        request
      )
    );
  }

  @PostMapping("/review-tasks/{taskId}/approve")
  public ApiResponse<SubmitReviewResult> approve(
    @PathVariable long taskId,
    @RequestBody(required = false) ReviewDecisionRequest request,
    HttpServletRequest servletRequest
  ) {
    if (!"INFO_MANAGER".equals(role(servletRequest))) {
      throw new ReviewOperationException(HttpStatus.FORBIDDEN, "当前用户无权执行初审");
    }
    SubmitReviewResult result = commandService.submit(
      reportId(taskId, servletRequest),
      operatorId(servletRequest),
      role(servletRequest),
      new SubmitReviewRequest("APPROVE", request == null ? null : request.reviewComment())
    );
    return new ApiResponse<>(200, "初审通过", result);
  }

  @PostMapping("/review-tasks/{taskId}/finalize")
  public ApiResponse<SubmitReviewResult> finalizeReview(
    @PathVariable long taskId,
    @RequestBody(required = false) ReviewDecisionRequest request,
    HttpServletRequest servletRequest
  ) {
    if (!"DEPT_MANAGER".equals(role(servletRequest))) {
      throw new ReviewOperationException(HttpStatus.FORBIDDEN, "当前用户无终审权限");
    }
    SubmitReviewResult result = commandService.submit(
      reportId(taskId, servletRequest),
      operatorId(servletRequest),
      role(servletRequest),
      new SubmitReviewRequest("APPROVE", request == null ? null : request.reviewComment())
    );
    return new ApiResponse<>(200, "终审通过，报告进入待发送状态", result);
  }

  @PostMapping("/review-tasks/{taskId}/reject")
  public ApiResponse<SubmitReviewResult> reject(
    @PathVariable long taskId,
    @RequestBody ReviewDecisionRequest request,
    HttpServletRequest servletRequest
  ) {
    if (request == null || request.reviewComment() == null || request.reviewComment().isBlank()) {
      throw new ReviewOperationException(HttpStatus.BAD_REQUEST, "退回原因不能为空");
    }
    SubmitReviewResult result = commandService.submit(
      reportId(taskId, servletRequest),
      operatorId(servletRequest),
      role(servletRequest),
      new SubmitReviewRequest("REJECT", request.reviewComment())
    );
    return new ApiResponse<>(200, "审核退回成功", result);
  }

  @GetMapping("/reports/{reportId}/issues")
  public ApiResponse<List<ReviewIssue>> issues(@PathVariable long reportId) {
    return new ApiResponse<>(200, "查询成功", issueService.issues(reportId));
  }

  @PostMapping("/reports/{reportId}/check")
  public ApiResponse<CheckResult> check(
    @PathVariable long reportId,
    HttpServletRequest servletRequest
  ) {
    requireReviewer(servletRequest);
    return new ApiResponse<>(200, "审核检测完成", issueService.check(reportId));
  }

  @PutMapping("/review-issues/{id}/resolve")
  public ApiResponse<Void> resolveIssue(@PathVariable long id, HttpServletRequest servletRequest) {
    requireReviewer(servletRequest);
    issueService.resolve(id, operatorId(servletRequest));
    return new ApiResponse<>(200, "审核问题已标记为已处理", null);
  }

  @ExceptionHandler(ReviewOperationException.class)
  public ResponseEntity<ApiResponse<Object>> reviewOperationError(
    ReviewOperationException exception
  ) {
    return ResponseEntity.status(exception.status()).body(
      new ApiResponse<>(exception.status().value(), exception.getMessage(), exception.data())
    );
  }

  private long operatorId(HttpServletRequest request) {
    Object userId = request.getAttribute("reviewUserId");
    if (userId instanceof Long id) return id;
    throw new ReviewOperationException(HttpStatus.FORBIDDEN, "无权操作审核任务");
  }

  private String role(HttpServletRequest request) {
    return String.valueOf(request.getAttribute("reviewRoleCode"));
  }

  private long reportId(long taskOrReportId, HttpServletRequest request) {
    return reviewRepository
      .findAssignedReportId(taskOrReportId, operatorId(request))
      .orElseThrow(() ->
        new ReviewOperationException(HttpStatus.NOT_FOUND, "审核任务不存在或不属于当前用户")
      );
  }

  public record ReviewDecisionRequest(String reviewComment) {}

  private void requireReviewer(HttpServletRequest request) {
    if (!List.of("INFO_MANAGER", "DEPT_MANAGER").contains(role(request))) {
      throw new ReviewOperationException(HttpStatus.FORBIDDEN, "当前用户无权执行审核操作");
    }
  }
}
