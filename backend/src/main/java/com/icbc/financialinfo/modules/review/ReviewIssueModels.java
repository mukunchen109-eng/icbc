package com.icbc.financialinfo.modules.review;

public final class ReviewIssueModels {
    private ReviewIssueModels() {}

    public record ReviewIssue(
            Long id, Long reportId, Long articleId, String issueType, String matchedText,
            Integer startOffset, Integer endOffset, String message, Integer resolved,
            Long resolvedBy, String resolvedAt, String createdAt) {}

    public record CheckResult(
            Long reportId, Integer issueCount, Integer sensitiveCount,
            Integer dataInconsistencyCount) {}
}
