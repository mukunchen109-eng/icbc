package com.icbc.financialinfo.modules.report.controller;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.report.model.GenerateDailySummaryRequest;
import com.icbc.financialinfo.modules.report.model.GeneratedReportResponse;
import com.icbc.financialinfo.modules.report.model.ReportListItem;
import com.icbc.financialinfo.modules.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
