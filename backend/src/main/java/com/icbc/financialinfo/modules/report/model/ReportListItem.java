package com.icbc.financialinfo.modules.report.model;

import java.time.Instant;
import java.time.LocalDate;

public record ReportListItem(
  String reportId,
  String reportTitle,
  LocalDate reportDate,
  int articleCount,
  Instant generatedAt,
  String wordFileName,
  String pdfFileName
) {}
