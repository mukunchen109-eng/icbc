package com.icbc.financialinfo.modules.auth;

import com.icbc.financialinfo.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Validated @RestController @RequestMapping("/api/auth")
public class AuthController {
    @PostMapping("/login") public ApiResponse<Map<String, Object>> login(@RequestBody @Validated LoginRequest request) {
        return ApiResponse.ok(Map.of("token", "dev-token", "user", Map.of("username", request.username(), "role", "ADMIN")));
    }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
