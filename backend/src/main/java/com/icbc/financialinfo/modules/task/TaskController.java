package com.icbc.financialinfo.modules.task;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public ApiResponse<List<TaskRepository.TaskRecord>> list() {
        return ApiResponse.ok(taskRepository.findAll());
    }
}