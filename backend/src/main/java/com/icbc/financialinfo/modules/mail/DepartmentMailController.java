package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.mail.DepartmentMailService.CreateRequest;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail-tasks")
public class DepartmentMailController {

  private final DepartmentMailService mailService;

  public DepartmentMailController(DepartmentMailService mailService) {
    this.mailService = mailService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<DepartmentMailService.CreateResult>> create(
    HttpServletRequest servletRequest,
    @RequestBody @Valid CreateMailRequest request
  ) {
    try {
      CreateRequest serviceRequest = new CreateRequest(
        request.reportId(),
        request.subject(),
        request.mailBody(),
        request
          .recipients()
          .stream()
          .map(item -> new DepartmentMailService.Recipient(item.name(), item.email()))
          .toList()
      );
      return ResponseEntity.ok(
        new ApiResponse<>(
          200,
          "邮件任务创建成功",
          mailService.create(managerId(servletRequest), serviceRequest)
        )
      );
    } catch (DepartmentBusinessException exception) {
      return error(exception);
    }
  }

  @PostMapping("/{id}/send")
  public ResponseEntity<ApiResponse<DepartmentMailService.SendResult>> send(
    HttpServletRequest servletRequest,
    @PathVariable long id
  ) {
    try {
      DepartmentMailService.SendResult result = mailService.send(managerId(servletRequest), id);
      String message = result.failedCount() == 0
        ? "邮件发送成功，报告已归档"
        : "邮件发送完成，部分收件人失败";
      return ResponseEntity.ok(new ApiResponse<>(200, message, result));
    } catch (DepartmentBusinessException exception) {
      return error(exception);
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DepartmentMailService.MailTaskDetail>> detail(
    HttpServletRequest request,
    @PathVariable long id
  ) {
    try {
      return ResponseEntity.ok(
        new ApiResponse<>(200, "查询成功", mailService.detail(managerId(request), id))
      );
    } catch (DepartmentBusinessException exception) {
      return error(exception);
    }
  }

  @GetMapping("/{id}/logs")
  public ResponseEntity<ApiResponse<List<DepartmentMailService.MailLogDetail>>> logs(
    HttpServletRequest request,
    @PathVariable long id
  ) {
    try {
      return ResponseEntity.ok(
        new ApiResponse<>(200, "查询成功", mailService.logs(managerId(request), id))
      );
    } catch (DepartmentBusinessException exception) {
      return error(exception);
    }
  }

  private <T> ResponseEntity<ApiResponse<T>> error(DepartmentBusinessException exception) {
    return ResponseEntity.status(exception.status()).body(
      new ApiResponse<>(exception.status(), exception.getMessage(), null)
    );
  }

  private long managerId(HttpServletRequest request) {
    if (!"DEPT_MANAGER".equals(request.getAttribute("reviewRoleCode"))) {
      throw new DepartmentBusinessException(403, "只有部门负责人可以发送报告");
    }
    Object userId = request.getAttribute("reviewUserId");
    if (userId instanceof Long id) return id;
    throw new DepartmentBusinessException(403, "无权执行邮件操作");
  }

  public record CreateMailRequest(
    @NotNull Long reportId,
    @NotBlank @Size(max = 300) String subject,
    @Size(max = 10000) String mailBody,
    @NotEmpty List<@Valid RecipientRequest> recipients
  ) {}

  public record RecipientRequest(@NotBlank String name, @NotBlank @Email String email) {}
}
