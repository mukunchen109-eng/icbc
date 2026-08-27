package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.review.ReviewCommandModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ReviewDraftService {
    private final ReviewCommandService commandService;
    private final ReviewIssueService issueService;

    public ReviewDraftService(ReviewCommandService commandService, ReviewIssueService issueService) {
        this.commandService = commandService;
        this.issueService = issueService;
    }

    @Transactional
    public SaveDraftResult save(long reportId, long userId, String role, SaveDraftRequest request) {
        if (!"INFO_MANAGER".equals(role)) {
            throw new ReviewOperationException(HttpStatus.FORBIDDEN, "部门负责人不能修改审核草稿");
        }
        List<DraftOperation> operations = request == null || request.operations() == null
                ? List.of() : request.operations();
        if (operations.size() > 500) {
            throw new ReviewOperationException(HttpStatus.BAD_REQUEST, "单次保存的草稿操作过多");
        }

        String reportStatus = null;
        for (DraftOperation operation : operations) {
            if (operation == null || operation.type() == null) {
                throw new ReviewOperationException(HttpStatus.BAD_REQUEST, "草稿操作类型不能为空");
            }
            String type = operation.type().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "MODIFY" -> reportStatus = commandService.modifyArticle(
                        reportId, required(operation.articleId(), "报告条目不能为空"), userId, role,
                        new ModifyArticleRequest(operation.title(), operation.summaryContent(), operation.reason()))
                        .reportStatus();
                case "MARK_MODIFY" -> reportStatus = commandService.addMark(
                        reportId, userId, role,
                        new AddMarkRequest(required(operation.articleId(), "报告条目不能为空"),
                                "MODIFY", operation.selectedText()))
                        .recordId() == null ? reportStatus : "INITIAL_REVIEWING";
                case "COMMENT" -> reportStatus = commandService.addComment(
                        reportId, userId, role,
                        new AddCommentRequest(required(operation.articleId(), "报告条目不能为空"),
                                operation.selectedText(), operation.commentText()))
                        .recordId() == null ? reportStatus : "INITIAL_REVIEWING";
                case "REPLACE" -> reportStatus = commandService.replaceArticle(
                        reportId, required(operation.articleId(), "报告条目不能为空"), userId, role,
                        new ReplaceArticleRequest(required(operation.newNewsId(), "替换资讯不能为空"), operation.reason()))
                        .reportStatus();
                case "RESOLVE_ISSUE" -> issueService.resolve(
                        required(operation.issueId(), "审核问题不能为空"), userId);
                default -> throw new ReviewOperationException(HttpStatus.BAD_REQUEST, "不支持的草稿操作：" + type);
            }
        }
        return new SaveDraftResult(operations.size(), reportStatus);
    }

    private long required(Long value, String message) {
        if (value == null) throw new ReviewOperationException(HttpStatus.BAD_REQUEST, message);
        return value;
    }
}
