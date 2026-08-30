package com.icbc.financialinfo.modules.task;

import com.icbc.financialinfo.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final TaskRepository taskRepository;

  public TaskController(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @GetMapping
  public ApiResponse<PageData<TaskRepository.TaskRecord>> list(
    @RequestParam(defaultValue = "1") int pageNum,
    @RequestParam(defaultValue = "10") int pageSize
  ) {
    int safePageNum = Math.max(pageNum, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    return new ApiResponse<>(
      200,
      "查询成功",
      new PageData<>(
        taskRepository.count(),
        safePageNum,
        safePageSize,
        taskRepository.findPage(safePageNum, safePageSize)
      )
    );
  }

  public record PageData<T>(long total, int pageNum, int pageSize, List<T> records) {}
}
