package com.icbc.financialinfo.modules.review;

import com.icbc.financialinfo.modules.user.UserRepository;
import com.icbc.financialinfo.modules.user.UserRepository.UserAccount;
import org.springframework.stereotype.Service;

@Service
public class DepartmentAuthorizationService {
    private static final String TOKEN_PREFIX = "Bearer dev-token-";
    private final UserRepository userRepository;

    public DepartmentAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserAccount requireDepartmentManager(String authorization) {
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            throw new DepartmentBusinessException(401, "请先登录");
        }
        String username = authorization.substring(TOKEN_PREFIX.length());
        return userRepository.findByUsername(username)
                .filter(user -> user.status() == 1 && "DEPT_MANAGER".equals(user.roleCode()))
                .orElseThrow(() -> new DepartmentBusinessException(403, "仅部室负责人可执行此操作"));
    }
}
