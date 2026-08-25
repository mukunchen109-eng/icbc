package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mail-recipients")
public class DepartmentMailRecipientController {
    private final DepartmentMailService mailService;

    public DepartmentMailRecipientController(DepartmentMailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentMailService.RecipientOption>>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(200, "查询成功", mailService.listRecipients(authorization)));
        } catch (DepartmentBusinessException exception) {
            return error(exception);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentMailService.RecipientOption>> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Valid CreateRecipientRequest request) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(200, "收件人新增成功",
                    mailService.addRecipient(authorization, request.name(), request.email())));
        } catch (DepartmentBusinessException exception) {
            return error(exception);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> error(DepartmentBusinessException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(exception.status(), exception.getMessage(), null));
    }

    public record CreateRecipientRequest(@NotBlank String name, @NotBlank @Email String email) {}
}
