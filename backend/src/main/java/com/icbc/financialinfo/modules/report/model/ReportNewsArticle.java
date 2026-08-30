package com.icbc.financialinfo.modules.report.model;

import jakarta.validation.constraints.NotBlank;

public record ReportNewsArticle(
  String id,
  @NotBlank(message = "资讯标题不能为空") String title,
  String category,
  String region,
  String sourceLabel,
  String sourceName,
  String publishedAt,
  String url,
  String summary,
  String content
) {}
