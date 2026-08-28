package com.icbc.financialinfo.modules.report.model;

import java.time.Instant;
import java.time.LocalDate;

public record GeneratedReportResponse(
        String reportId,
        String reportTitle,
        LocalDate reportDate,
        String content,
        int articleCount,
        Instant generatedAt,
        ReportFileDescriptor wordFile,
        ReportFileDescriptor pdfFile
) {
}
