package com.icbc.financialinfo.modules.report.model;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateDailySummaryRequest(
  @NotNull(message = "reportDate 不能为空") LocalDate reportDate,
  String reportTitle
) {}
