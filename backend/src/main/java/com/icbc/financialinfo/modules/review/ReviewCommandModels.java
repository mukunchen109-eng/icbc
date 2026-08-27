package com.icbc.financialinfo.modules.review;
import java.util.List;
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
 public record ReplacementArticle(Long articleId,Long newsId,String newsDate,String category,String title,
                                  String industry,String area,String summaryContent,String reason){}
 public record DraftOperation(String type,Long articleId,Long newNewsId,Long issueId,String markType,
                              String selectedText,String commentText,String title,String summaryContent,String reason){}
 public record SaveDraftRequest(List<DraftOperation> operations){}
 public record SaveDraftResult(int operationCount,String reportStatus){}
 public record SubmitReviewRequest(String decision,String comment){}
 public record SubmitReviewResult(Long reportId,String previousStatus,String reportStatus){}
}
