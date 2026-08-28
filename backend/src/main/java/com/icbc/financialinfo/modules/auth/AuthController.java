package com.icbc.financialinfo.modules.auth;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.user.UserRepository;
import com.icbc.financialinfo.modules.user.UserRepository.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody @Valid LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username().trim()).orElse(null);
        if (user == null || user.status() != 1 ||
                !passwordEncoder.matches(request.password(), user.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "账号或密码错误", null));
        }

        Map<String, Object> responseData = Map.of(
                "token", "dev-token-" + user.username(),
                "expiresIn", 7200,
                "user", Map.of(
                        "id", user.id(),
                        "username", user.username(),
                        "roleCode", user.roleCode(),
                        "roleName", user.roleName()
                )
        );
        return ResponseEntity.ok(new ApiResponse<>(200, "登录成功", responseData));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String prefix = "Bearer dev-token-";
        if (authorization == null || !authorization.startsWith(prefix)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "登录状态无效", null));
        }
        UserAccount user = userRepository.findByUsername(authorization.substring(prefix.length())).orElse(null);
        if (user == null || user.status() != 1) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "登录状态无效", null));
        }
        return ResponseEntity.ok(new ApiResponse<>(200, "查询成功", Map.of(
                "id", user.id(), "username", user.username(), "roleCode", user.roleCode(),
                "roleName", user.roleName(), "status", user.status())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return new ApiResponse<>(200, "退出成功", null);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
