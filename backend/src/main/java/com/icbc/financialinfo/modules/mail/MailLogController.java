package com.icbc.financialinfo.modules.mail;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.review.DepartmentBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail-logs")
public class MailLogController {
    private final DepartmentMailService mailService;
    public MailLogController(DepartmentMailService mailService) { this.mailService = mailService; }

    @PostMapping("/{id}/retry")
    public ApiResponse<DepartmentMailService.MailLogDetail> retry(@PathVariable long id, HttpServletRequest request) {
        return new ApiResponse<>(200, "重试记录已更新", mailService.retry(managerId(request), id));
    }

    private long managerId(HttpServletRequest request) {
        if (!"DEPT_MANAGER".equals(request.getAttribute("reviewRoleCode")))
            throw new DepartmentBusinessException(403, "只有部门负责人可以操作邮件任务");
        Object id = request.getAttribute("reviewUserId");
        if (id instanceof Long value) return value;
        throw new DepartmentBusinessException(403, "无权执行邮件操作");
    }
}
