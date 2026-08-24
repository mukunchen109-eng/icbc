package com.icbc.financialinfo.modules.user;
import com.icbc.financialinfo.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/users")
public class UserController {
  @GetMapping public ApiResponse<List<UserSummary>> list() { return ApiResponse.ok(List.of(new UserSummary(1L,"admin","系统管理员","ADMIN","启用"))); }
  public record UserSummary(Long id,String username,String displayName,String role,String status) {}
}
