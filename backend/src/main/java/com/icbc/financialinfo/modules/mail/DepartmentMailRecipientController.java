package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail-recipients")
public class DepartmentMailRecipientController {

  private final DepartmentMailService mailService;

  public DepartmentMailRecipientController(DepartmentMailService mailService) {
    this.mailService = mailService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<DepartmentMailService.RecipientOption>>> list(
    HttpServletRequest servletRequest
  ) {
    try {
      managerId(servletRequest);
      return ResponseEntity.ok(new ApiResponse<>(200, "查询成功", mailService.listRecipients()));
    } catch (DepartmentBusinessException exception) {
      return error(exception);
    }
  }

  @PostMapping
  public ResponseEntity<ApiResponse<DepartmentMailService.RecipientOption>> add(
    HttpServletRequest servletRequest,
    @RequestBody @Valid CreateRecipientRequest request
  ) {
    try {
      return ResponseEntity.ok(
        new ApiResponse<>(
          200,
          "收件人新增成功",
          mailService.addRecipient(managerId(servletRequest), request.name(), request.email())
        )
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
      throw new DepartmentBusinessException(403, "只有部门负责人可以管理收件人");
    }
    Object userId = request.getAttribute("reviewUserId");
    if (userId instanceof Long id) return id;
    throw new DepartmentBusinessException(403, "无权管理收件人");
  }

  public record CreateRecipientRequest(@NotBlank String name, @NotBlank @Email String email) {}
}
