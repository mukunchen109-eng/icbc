package com.icbc.financialinfo.modules.review;
public final class ReviewCommandModels{
 private ReviewCommandModels(){}
 public record ModifyArticleRequest(String title,String summaryContent,String reason){}
 public record ModifyArticleResult(Long articleId,Long reportId,String reportStatus){}
 public record AddCommentRequest(Long articleId,String selectedText,String commentText){}
 public record AddCommentResult(Long recordId){}
 public record AddMarkRequest(Long articleId,String markType,String selectedText){}
 public record AddMarkResult(Long recordId){}
 public record ReplaceArticleRequest(Long newNewsId,String reason){}
 public record ReplaceArticleResult(Long oldArticleId,Long articleId,Long newsId,Long reportId,String reportStatus){}
 public record ReplacementArticle(Long newsId,String newsDate,String title,String industry,String area){}
 public record SubmitReviewRequest(String decision,String comment){}
 public record SubmitReviewResult(Long reportId,String previousStatus,String reportStatus){}
}
