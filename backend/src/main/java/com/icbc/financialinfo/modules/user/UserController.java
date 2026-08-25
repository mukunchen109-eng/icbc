package com.icbc.financialinfo.modules.user;

import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.user.UserRepository.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<PageData<UserResponse>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer status) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        List<UserResponse> records = userRepository
                .findPage(safePageNum, safePageSize, username, roleId, status)
                .stream().map(this::toResponse).toList();
        long total = userRepository.count(username, roleId, status);
        return ApiResponse.ok(new PageData<>(total, safePageNum, safePageSize, records));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> detail(@PathVariable long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(toResponse(user))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(404, "用户不存在", null)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @RequestBody @Valid CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.usernameExists(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(409, "登录账号已存在", null));
        }
        if (!userRepository.roleExists(request.roleId())) return badRole();
        long id = userRepository.create(username, passwordEncoder.encode(request.password()), request.roleId());
        return ResponseEntity.ok(new ApiResponse<>(200, "用户创建成功", Map.of("id", id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable long id, @RequestBody @Valid UpdateUserRequest request) {
        UserAccount current = userRepository.findById(id).orElse(null);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "用户不存在", null));
        }
        if (!userRepository.roleExists(request.roleId())) return badRole();
        int newStatus = request.status() == null ? current.status() : request.status();
        if (newStatus != 0 && newStatus != 1) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "状态值必须为0或1", null));
        }
        userRepository.update(id, request.roleId(), newStatus);
        return ResponseEntity.ok(new ApiResponse<>(200, "用户信息修改成功", null));
    }

    private <T> ResponseEntity<ApiResponse<T>> badRole() {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "角色不存在", null));
    }

    private UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.id(), user.username(), user.roleId(),
                user.roleCode(), user.roleName(), user.status(), user.createdAt());
    }

    public record CreateUserRequest(@NotBlank @Size(max = 64) String username,
                                    @NotBlank @Size(min = 3, max = 100) String password,
                                    @NotNull Long roleId) {}
    public record UpdateUserRequest(@NotNull Long roleId, Integer status) {}
    public record PageData<T>(long total, int pageNum, int pageSize, List<T> records) {}
    public record UserResponse(Long id, String username, Long roleId,
                               String roleCode, String roleName, Integer status, String createdAt) {}
}
