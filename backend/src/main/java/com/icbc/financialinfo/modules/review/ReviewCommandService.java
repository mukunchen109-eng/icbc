package com.icbc.financialinfo.modules.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.AddCommentResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ModifyArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleRequest;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplaceArticleResult;
import com.icbc.financialinfo.modules.review.ReviewCommandModels.ReplacementArticle;
import com.icbc.financialinfo.modules.review.ReviewCommandRepository.ArticleState;
import com.icbc.financialinfo.modules.review.ReviewCommandRepository.NewsState;
import com.icbc.financialinfo.modules.review.ReviewCommandRepository.ReportState;
import com.icbc.financialinfo.modules.review.ReviewCommandRepository.TaskState;
import com.icbc.financialinfo.modules.review.ReviewCommandRepository.VersionState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewCommandService {
    private final ReviewCommandRepository repository;
    private final ObjectMapper objectMapper;

    public ReviewCommandService(ReviewCommandRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ModifyArticleResult modifyArticle(long taskId, long articleId, long operatorId,
                                             ModifyArticleRequest request) {
        requireText(request == null ? null : request.reason(), "修改原因不能为空");
        TaskAndReport context = writableContext(taskId, operatorId);
        VersionState sourceVersion = currentVersion(context.report());
        ArticleState sourceArticle = currentArticle(articleId, context.task(), sourceVersion);

        String title = optionalText(request.title(), sourceArticle.title(), 500, "报告标题不能为空");
        String summary = optionalText(
                request.summaryContent(), sourceArticle.summaryContent(), null, "报告摘要不能为空");
        String sourceLabel = optionalText(
                request.sourceLabel(), sourceArticle.sourceLabel(), 100, "来源标签不能为空");
        if (title.equals(sourceArticle.title())
                && summary.equals(sourceArticle.summaryContent())
                && sourceLabel.equals(sourceArticle.sourceLabel())) {
            throw badRequest("未提供需要修改的报告条目内容");
        }

        int newVersionNo = sourceVersion.versionNo() + 1;
        long newVersionId = repository.createVersion(
                context.task().reportId(), newVersionNo,
                versionType(context.task().reviewStage()), operatorId);
        repository.copyArticlesWithModification(
                sourceVersion.id(), newVersionId, articleId, title, summary, sourceLabel);
        ArticleState newArticle = repository.findArticle(newVersionId, sourceArticle.sequenceNo())
                .orElseThrow(() -> new IllegalStateException("新版本报告条目复制失败"));
        repository.advanceReportVersion(context.task().reportId(), newVersionNo, operatorId);
        repository.insertReviewRecord(
                taskId, context.task().reportId(), newVersionId, newArticle.id(), operatorId,
                "MODIFY", articleSnapshot(sourceArticle), articleSnapshot(newArticle),
                request.reason().trim(), null);
        return new ModifyArticleResult(newArticle.id(), newVersionId, newVersionNo);
    }

    @Transactional
    public AddCommentResult addComment(long taskId, long operatorId, AddCommentRequest request) {
        requireText(request == null ? null : request.commentText(), "批注内容不能为空");
        if (request.articleId() == null) throw badRequest("报告条目ID不能为空");
        TaskState task = assignedPendingTask(taskId, operatorId, false);
        ArticleState article = repository.findArticle(request.articleId())
                .orElseThrow(() -> notFound("报告条目不存在"));
        if (!article.reportId().equals(task.reportId())) throw notFound("报告条目不存在");

        long recordId = repository.insertReviewRecord(
                task.id(), task.reportId(), article.versionId(), article.id(), operatorId,
                "ANNOTATE", null, null, null, request.commentText().trim());
        return new AddCommentResult(recordId);
    }

    @Transactional
    public ReplaceArticleResult replaceArticle(long taskId, long articleId, long operatorId,
                                               ReplaceArticleRequest request) {
        requireText(request == null ? null : request.reason(), "替换原因不能为空");
        if (request.newNewsId() == null) throw badRequest("替换资讯ID不能为空");
        TaskAndReport context = writableContext(taskId, operatorId);
        VersionState sourceVersion = currentVersion(context.report());
        ArticleState sourceArticle = currentArticle(articleId, context.task(), sourceVersion);
        NewsState news = repository.findNews(request.newNewsId())
                .orElseThrow(() -> notFound("替换资讯不存在"));

        int newVersionNo = sourceVersion.versionNo() + 1;
        long newVersionId = repository.createVersion(
                context.task().reportId(), newVersionNo,
                versionType(context.task().reviewStage()), operatorId);
        repository.copyArticlesWithReplacement(
                sourceVersion.id(), newVersionId, articleId, news);
        ArticleState newArticle = repository.findArticle(newVersionId, sourceArticle.sequenceNo())
                .orElseThrow(() -> new IllegalStateException("新版本报告条目复制失败"));
        repository.advanceReportVersion(context.task().reportId(), newVersionNo, operatorId);
        repository.insertReviewRecord(
                taskId, context.task().reportId(), newVersionId, newArticle.id(), operatorId,
                "REPLACE", articleSnapshot(sourceArticle), articleSnapshot(newArticle),
                request.reason().trim(), null);
        return new ReplaceArticleResult(
                articleId, newArticle.id(), news.id(), newVersionId, newVersionNo);
    }

    @Transactional(readOnly = true)
    public List<ReplacementArticle> replacementArticles(
            long taskId, long operatorId, String category, String keyword) {
        TaskState task = assignedPendingTask(taskId, operatorId, false);
        ReportState report = repository.findReport(task.reportId())
                .orElseThrow(() -> notFound("报告不存在"));
        VersionState currentVersion = currentVersion(report);
        List<ReplacementArticle> records = repository.findReplacementArticles(
                currentVersion.id(), report.reportDate(), category, keyword);
        if (records.isEmpty()) {
            throw new ReviewOperationException(
                    HttpStatus.NOT_FOUND, "未找到可替换资讯", List.of());
        }
        return records;
    }

    private TaskAndReport writableContext(long taskId, long operatorId) {
        TaskState task = assignedPendingTask(taskId, operatorId, true);
        ReportState report = repository.lockReport(task.reportId())
                .orElseThrow(() -> notFound("报告不存在"));
        if (report.locked() == 1
                && report.lockedBy() != null
                && !report.lockedBy().equals(operatorId)) {
            throw new ReviewOperationException(
                    HttpStatus.CONFLICT, "报告已被其他审核人员锁定");
        }
        String expectedStatus = versionType(task.reviewStage());
        if (!expectedStatus.equals(report.status())) {
            throw new ReviewOperationException(HttpStatus.CONFLICT, "报告当前审核阶段不匹配");
        }
        return new TaskAndReport(task, report);
    }

    private TaskState assignedPendingTask(long taskId, long operatorId, boolean forUpdate) {
        TaskState task = (forUpdate
                ? repository.findTaskForUpdate(taskId)
                : repository.findTask(taskId))
                .orElseThrow(() -> notFound("审核任务不存在"));
        if (!task.reviewerId().equals(operatorId)) {
            throw new ReviewOperationException(HttpStatus.FORBIDDEN, "无权操作该审核任务");
        }
        if (!"PENDING".equals(task.status())) {
            throw new ReviewOperationException(HttpStatus.CONFLICT, "审核任务已完成");
        }
        return task;
    }

    private VersionState currentVersion(ReportState report) {
        return repository.findVersion(report.id(), report.currentVersionNo())
                .orElseThrow(() -> notFound("报告当前版本不存在"));
    }

    private ArticleState currentArticle(
            long articleId, TaskState task, VersionState currentVersion) {
        ArticleState article = repository.findArticle(articleId)
                .orElseThrow(() -> notFound("报告条目不存在"));
        if (!article.reportId().equals(task.reportId())) throw notFound("报告条目不存在");
        if (!article.versionId().equals(currentVersion.id())) {
            throw new ReviewOperationException(
                    HttpStatus.CONFLICT, "报告版本已更新，请刷新后重试");
        }
        return article;
    }

    private String versionType(String reviewStage) {
        return "FINAL".equals(reviewStage) ? "FINAL_REVIEW" : "INITIAL_REVIEW";
    }

    private String optionalText(
            String requested, String current, Integer maxLength, String emptyMessage) {
        if (requested == null) return current;
        String value = requested.trim();
        if (value.isEmpty()) throw badRequest(emptyMessage);
        if (maxLength != null && value.length() > maxLength) {
            throw badRequest(emptyMessage.replace("不能为空", "长度超出限制"));
        }
        return value;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
    }

    private String articleSnapshot(ArticleState article) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("articleId", article.id());
        snapshot.put("newsId", article.newsId());
        snapshot.put("sequenceNo", article.sequenceNo());
        snapshot.put("category", article.category());
        snapshot.put("title", article.title());
        snapshot.put("summaryContent", article.summaryContent());
        snapshot.put("sourceLabel", article.sourceLabel());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核记录序列化失败", exception);
        }
    }

    private ReviewOperationException badRequest(String message) {
        return new ReviewOperationException(HttpStatus.BAD_REQUEST, message);
    }

    private ReviewOperationException notFound(String message) {
        return new ReviewOperationException(HttpStatus.NOT_FOUND, message);
    }

    private record TaskAndReport(TaskState task, ReportState report) {}
}
