package com.icbc.financialinfo.modules.report.model;

public record ReportFileDescriptor(
        String format,
        String fileName,
        String absolutePath,
        String downloadUrl
) {
}
