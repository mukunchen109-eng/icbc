package com.icbc.financialinfo.modules.archive;

import com.icbc.financialinfo.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/archives")
public class ArchiveController {

  @GetMapping("/summary")
  public ApiResponse<Map<String, Integer>> summary() {
    return ApiResponse.ok(Map.of("reports", 0, "auditLogs", 0, "mailLogs", 0));
  }
}
