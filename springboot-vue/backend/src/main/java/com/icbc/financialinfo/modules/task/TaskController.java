package com.icbc.financialinfo.modules.task;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/tasks")
public class TaskController { @GetMapping public ApiResponse<List<TaskSummary>> list() { return ApiResponse.ok(List.of(new TaskSummary("daily-collection","每日资讯采集","09:00","待配置"))); } public record TaskSummary(String code,String name,String cron,String status) {} }
