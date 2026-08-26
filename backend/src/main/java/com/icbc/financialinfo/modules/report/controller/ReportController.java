package com.icbc.financialinfo.modules.report.controller;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<List<ReportListItem>> list() {
        return ApiResponse.ok(reportService.listReports());
    }

    @GetMapping("/{reportId}")
    public ApiResponse<GeneratedReportResponse> detail(@PathVariable String reportId) {
        return ApiResponse.ok(reportService.getReport(reportId));
    }

    @PostMapping("/daily-summary")
    public ApiResponse<GeneratedReportResponse> generateDailySummary(@RequestBody @Valid GenerateDailySummaryRequest request) {
        return ApiResponse.ok(reportService.generateDailySummary(request));
    }

    @GetMapping("/{reportId}/files/{format}")
    public ResponseEntity<Resource> download(@PathVariable String reportId, @PathVariable String format) {
        Path filePath = reportService.resolveReportFile(reportId, format);
        Resource resource = new FileSystemResource(filePath);
        MediaType mediaType = "pdf".equalsIgnoreCase(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filePath.getFileName().toString()).build().toString())
                .body(resource);
    }
}
