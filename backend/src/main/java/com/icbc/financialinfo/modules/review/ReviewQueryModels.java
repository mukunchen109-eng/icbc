package com.icbc.financialinfo.modules.review;

import java.util.List;

/** Response models used by the read-only review APIs. */
public final class ReviewQueryModels {
    private ReviewQueryModels() {}

    public record PageData<T>(long total, int pageNum, int pageSize, List<T> records) {}

    public record AssignedTaskSummary(
            Long id, Long reportId, Long versionId, Integer versionNo,
            String reviewStage, String status, String reportDate, String reportTitle,
            String submittedAt, String completedAt, String wordFilePath, String pdfFilePath) {}

    public record ReportSummary(
            Long id, String reportDate, String reportTitle, String status,
            Integer currentVersionNo, Integer locked, Long lockedBy, String lockedAt,
            String createdAt, String updatedAt) {}

    public record VersionDetail(
            Long id, Integer versionNo, String versionType, String wordFilePath,
            String pdfFilePath, Long createdBy, String createdAt) {}

    public record ReviewTaskDetail(
            Long id, String reviewStage, Long reviewerId, String status,
            String submittedAt, String completedAt) {}

    public record ReportReviewDetail(
            Long id, String reportDate, String reportTitle, String status,
            Integer currentVersionNo, Integer locked, Long lockedBy, String lockedAt,
            VersionDetail currentVersion, ReviewTaskDetail reviewTask) {}

    public record ReportArticle(
            Long id, Long versionId, Long newsId, Integer sequenceNo, String category,
            String title, String summaryContent, String sourceLabel, String createdAt) {}

    public record ArticleSource(
            Long articleId, Long newsId, String sourceRowId, String newsDate, String title,
            String originalContent, String content, String industry, String area,
            String contentHash) {}

    public record VersionSummary(
            Long id, Integer versionNo, String versionType, Long createdBy, String createdAt) {}
}
