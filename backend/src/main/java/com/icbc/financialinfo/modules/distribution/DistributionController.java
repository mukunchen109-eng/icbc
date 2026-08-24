package com.icbc.financialinfo.modules.distribution;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/distributions")
public class DistributionController { @PostMapping("/send") public ApiResponse<Map<String,String>> send() { return ApiResponse.ok(Map.of("message","邮件分发任务已提交")); } }
