package com.icbc.financialinfo.modules.preprocess;

import com.icbc.financialinfo.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preprocess")
public class PreprocessController {

  @GetMapping("/status")
  public ApiResponse<Map<String, Object>> status() {
    return ApiResponse.ok(Map.of("pending", 0, "processed", 0, "deduplicated", 0));
  }
}
