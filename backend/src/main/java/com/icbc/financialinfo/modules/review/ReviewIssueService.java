package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.review.ReviewIssueModels.CheckResult;
import com.icbc.financialinfo.modules.review.ReviewIssueModels.ReviewIssue;
import com.icbc.financialinfo.modules.review.ReviewIssueRepository.CheckArticle;
import com.icbc.financialinfo.modules.review.ReviewIssueRepository.IssueState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewIssueService {

  private static final List<String> SENSITIVE_WORDS = List.of(
    "绝对安全",
    "保证收益",
    "内幕消息",
    "暴涨",
    "稳赚不赔",
    "重大利好",
    "待核验内容"
  );
  private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?(?:[%％])?");

  private final ReviewIssueRepository repository;
  private final Set<Long> checkingReports = ConcurrentHashMap.newKeySet();

  public ReviewIssueService(ReviewIssueRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<ReviewIssue> issues(long reportId) {
    requireReport(reportId);
    return repository.findIssues(reportId);
  }

  public CheckResult check(long reportId) {
    requireReport(reportId);
    if (!checkingReports.add(reportId)) {
      throw new ReviewOperationException(HttpStatus.CONFLICT, "当前报告正在检测，请稍后重试");
    }
    try {
      return executeCheck(reportId);
    } finally {
      checkingReports.remove(reportId);
    }
  }

  @Transactional
  protected CheckResult executeCheck(long reportId) {
    List<CheckArticle> articles = repository.findCheckArticles(reportId);
    repository.deleteIssues(reportId);
    int sensitiveCount = 0;
    int dataCount = 0;

    for (CheckArticle article : articles) {
      String summary = article.summaryContent() == null ? "" : article.summaryContent();
      String source = normalizeNumberText(
        article.sourceContent() == null ? "" : article.sourceContent()
      );

      for (String word : SENSITIVE_WORDS) {
        int fromIndex = 0;
        int position;
        while ((position = summary.indexOf(word, fromIndex)) >= 0) {
          repository.insertIssue(
            article.articleId(),
            "SENSITIVE_CONTENT",
            word,
            position,
            position + word.length(),
            "命中敏感内容，请人工复核"
          );
          sensitiveCount++;
          fromIndex = position + word.length();
        }
      }

      Matcher matcher = NUMBER_PATTERN.matcher(summary);
      Set<String> checkedAtOffset = new LinkedHashSet<>();
      while (matcher.find()) {
        String matched = matcher.group();
        String identity = matcher.start() + ":" + matched;
        if (!checkedAtOffset.add(identity)) continue;
        if (!source.contains(normalizeNumberText(matched))) {
          repository.insertIssue(
            article.articleId(),
            "DATA_INCONSISTENCY",
            matched,
            matcher.start(),
            matcher.end(),
            "报告数据与原始资讯数据不一致"
          );
          dataCount++;
        }
      }
    }
    return new CheckResult(reportId, sensitiveCount + dataCount, sensitiveCount, dataCount);
  }

  @Transactional
  public void resolve(long issueId, long operatorId) {
    IssueState issue = repository
      .lockIssue(issueId)
      .orElseThrow(() -> new ReviewOperationException(HttpStatus.NOT_FOUND, "审核问题不存在"));
    if (issue.resolved() == 1) {
      throw new ReviewOperationException(HttpStatus.CONFLICT, "该审核问题已处理");
    }
    repository.resolveIssue(issueId, operatorId);
  }

  private void requireReport(long reportId) {
    if (!repository.reportExists(reportId)) {
      throw new ReviewOperationException(HttpStatus.NOT_FOUND, "报告不存在");
    }
  }

  private String normalizeNumberText(String value) {
    return value.replace('％', '%').replace(",", "").replace("，", "");
  }
}
