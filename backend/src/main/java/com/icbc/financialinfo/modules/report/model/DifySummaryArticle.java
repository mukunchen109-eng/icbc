package com.icbc.financialinfo.modules.report.model;

public record DifySummaryArticle(
  Long newsId,
  String category,
  String title,
  String summaryContent,
  String sourceLabel,
  String reason,
  String selectType
) {}
