package com.icbc.financialinfo.modules.review;
import java.util.List;
public final class ReviewQueryModels {
 private ReviewQueryModels(){}
 public record PageData<T>(long total,int pageNum,int pageSize,List<T> records){}
 public record AssignedTaskSummary(Long id,Long reportId,String reviewStage,String status,String reportDate,String reportTitle,String submittedAt,String completedAt){}
 public record ReportSummary(Long id,String reportDate,String reportTitle,String status,Integer locked,Long lockedBy,String lockedAt,String createdAt,String updatedAt){}
 public record ReportReviewDetail(Long id,String reportDate,String reportTitle,String status,Integer locked,Long lockedBy,String lockedAt,String createdAt,String updatedAt,String departmentReviewComment){}
 public record ReportArticle(Long id,Long reportId,Long newsId,Integer sequenceNo,String category,String title,String summaryContent,String createdAt){}
 public record ArticleSource(Long articleId,Long newsId,Integer dailySeq,String sourceRowId,String newsDate,String title,String originalContent,String content,String industry,String area,String contentHash){}
 public record ReviewRecordView(Long id,Long articleId,Integer sequenceNo,Long operatorId,String actionType,String selectedText,String reason,String commentText,String createdAt){}
}
