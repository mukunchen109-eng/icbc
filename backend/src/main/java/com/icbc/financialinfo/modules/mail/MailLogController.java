package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail-logs")
public class MailLogController {

  private final DepartmentMailService mailService;

  public MailLogController(DepartmentMailService mailService) {
    this.mailService = mailService;
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<ApiResponse<DepartmentMailService.MailLogDetail>> retry(
    @PathVariable long id,
    HttpServletRequest request
  ) {
    try {
      return ResponseEntity.ok(
        new ApiResponse<>(200, "邮件重试完成", mailService.retry(managerId(request), id))
      );
    } catch (DepartmentBusinessException exception) {
      return ResponseEntity.status(exception.status()).body(
        new ApiResponse<>(exception.status(), exception.getMessage(), null)
      );
    }
  }

  private long managerId(HttpServletRequest request) {
    if (
      !"DEPT_MANAGER".equals(request.getAttribute("reviewRoleCode"))
    ) throw new DepartmentBusinessException(403, "只有部门负责人可以操作邮件任务");
    Object id = request.getAttribute("reviewUserId");
    if (id instanceof Long value) return value;
    throw new DepartmentBusinessException(403, "无权执行邮件操作");
  }
}
