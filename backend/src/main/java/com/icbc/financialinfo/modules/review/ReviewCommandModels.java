package com.icbc.financialinfo.modules.review;

public final class ReviewCommandModels {
    private ReviewCommandModels() {}

    public record ModifyArticleRequest(
            String title, String summaryContent, String sourceLabel, String reason) {}

    public record ModifyArticleResult(Long articleId, Long versionId, Integer versionNo) {}

    public record AddCommentRequest(Long articleId, String commentText) {}

    public record AddCommentResult(Long recordId) {}

    public record ReplaceArticleRequest(Long newNewsId, String reason) {}

    public record ReplaceArticleResult(
            Long oldArticleId, Long newArticleId, Long newsId,
            Long versionId, Integer versionNo) {}

    public record ReplacementArticle(
            Long newsId, String newsDate, String title, String industry, String area) {}
}
