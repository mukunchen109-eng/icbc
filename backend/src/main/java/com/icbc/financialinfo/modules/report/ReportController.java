package com.icbc.financialinfo.modules.report;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/reports")
public class ReportController { @GetMapping public ApiResponse<Map<String,Object>> list() { return ApiResponse.ok(Map.of("items", java.util.List.of(), "total", 0)); } }
