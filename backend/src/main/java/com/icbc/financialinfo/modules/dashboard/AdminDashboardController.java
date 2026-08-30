package com.icbc.financialinfo.modules.dashboard;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.dashboard.AdminDashboardRepository.JobSnapshot;
import com.icbc.financialinfo.modules.dashboard.AdminDashboardRepository.LatestSnapshot;
import com.icbc.financialinfo.modules.dashboard.AdminDashboardRepository.ReportSnapshot;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

  private final AdminDashboardRepository repository;

  public AdminDashboardController(AdminDashboardRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public ApiResponse<DashboardOverview> overview() {
    return new ApiResponse<>(
      200,
      "查询成功",
      new DashboardOverview(
        generationStatus(),
        new ModuleOverview(repository.reviewCounts(), repository.latestReport().orElse(null)),
        new ModuleOverview(repository.distributionCounts(), repository.latestMail().orElse(null)),
        new ModuleOverview(repository.taskCounts(), repository.latestJob().orElse(null)),
        new ModuleOverview(repository.userCounts(), null)
      )
    );
  }

  private GenerationStatus generationStatus() {
    Optional<ReportSnapshot> report = repository.todayReport();
    if (report.isPresent()) {
      ReportSnapshot value = report.get();
      return new GenerationStatus("GENERATED", "今日智能报告已生成", value.title(), value.status());
    }
    Optional<JobSnapshot> job = repository.todayJob();
    if (job.isEmpty()) return new GenerationStatus(
      "NOT_STARTED",
      "今日生成任务尚未开始",
      "等待每日资讯采集任务",
      null
    );
    JobSnapshot value = job.get();
    String status = value.status() == null ? "UNKNOWN" : value.status().toUpperCase();
    String title = switch (status) {
      case "RUNNING", "PROCESSING", "SENDING" -> "今日智能报告生成中";
      case "SUCCESS", "COMPLETED" -> "资讯采集已完成，等待生成报告";
      case "FAILED" -> "今日智能报告生成失败";
      default -> "今日智能报告等待处理";
    };
    String detail = value.message() == null || value.message().isBlank()
      ? "任务开始时间：" + (value.startedAt() == null ? "-" : value.startedAt())
      : value.message();
    return new GenerationStatus(status, title, detail, null);
  }

  public record DashboardOverview(
    GenerationStatus generation,
    ModuleOverview review,
    ModuleOverview distribution,
    ModuleOverview tasks,
    ModuleOverview users
  ) {}

  public record GenerationStatus(String status, String title, String detail, String reportStatus) {}

  public record ModuleOverview(Map<String, Long> counts, LatestSnapshot latest) {}
}
