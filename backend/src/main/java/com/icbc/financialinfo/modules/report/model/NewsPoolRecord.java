package com.icbc.financialinfo.modules.report.model;

public record NewsPoolRecord(
  String newsDate,
  String title,
  String content,
  String industry,
  String area,
  String dailySeq
) {}
