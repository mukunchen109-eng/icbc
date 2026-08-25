package com.icbc.financialinfo.modules.collection;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/collections")
public class CollectionController {
  @GetMapping("/status") public ApiResponse<Map<String,Object>> status() { return ApiResponse.ok(Map.of("latestRun", "待配置", "total", 0, "source", "IPA / Excel")); }
  @PostMapping("/run") public ApiResponse<Map<String,String>> run() { return ApiResponse.ok(Map.of("message", "已提交采集任务")); }
}
