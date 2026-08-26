package com.icbc.financialinfo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReviewAuthorizationFilter extends OncePerRequestFilter {
    private static final String TOKEN_PREFIX = "Bearer dev-token-";
    private static final Set<String> REVIEW_ROLES = Set.of("INFO_MANAGER", "DEPT_MANAGER");
    private static final Pattern REPORT_SUBRESOURCE = Pattern.compile(
            "^/api/reports/[^/]+/(review-detail|versions)$");
    private static final Pattern REPORT_REVIEW_RESOURCE = Pattern.compile(
            "^/api/reports/[^/]+/(articles|sources|issues|check|review-records)$");
    private static final Pattern ARTICLE_SOURCE = Pattern.compile(
            "^/api/report-articles/[^/]+/source$");
    private static final Pattern ISSUE_RESOLVE = Pattern.compile(
            "^/api/review-issues/[^/]+/resolve$");
    private static final Pattern REVIEW_TASK_ACTION = Pattern.compile(
            "^/api/review-tasks/[^/]+/(comments|marks|records|replacement-articles|submit|approve|reject|finalize)$");
    private static final Pattern REVIEW_TASK_DETAIL = Pattern.compile(
            "^/api/review-tasks/[^/]+$");
    private static final Pattern REVIEW_TASK_ARTICLE_ACTION = Pattern.compile(
            "^/api/review-tasks/[^/]+/articles/[^/]+(?:/replace)?$");

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public ReviewAuthorizationFilter(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean reviewApi = "/api/reports/review".equals(path)
                || "/api/review-tasks/my".equals(path)
                || path.startsWith("/api/mail-tasks")
                || path.startsWith("/api/mail-logs")
                || path.startsWith("/api/mail-recipients")
                || REPORT_SUBRESOURCE.matcher(path).matches()
                || REPORT_REVIEW_RESOURCE.matcher(path).matches()
                || ARTICLE_SOURCE.matcher(path).matches()
                || ISSUE_RESOLVE.matcher(path).matches()
                || REVIEW_TASK_ACTION.matcher(path).matches()
                || REVIEW_TASK_DETAIL.matcher(path).matches()
                || REVIEW_TASK_ARTICLE_ACTION.matcher(path).matches();
        return !reviewApi;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(TOKEN_PREFIX)) {
            String username = authorization.substring(TOKEN_PREFIX.length());
            final com.icbc.financialinfo.modules.user.UserRepository.UserAccount user;
            try {
                user = userRepository.findByUsername(username).orElse(null);
            } catch (DataAccessException exception) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        new ApiResponse<>(503, "数据库连接暂不可用，请稍后重试", null));
                return;
            }
            String effectiveRole = user != null && Long.valueOf(2L).equals(user.roleId())
                    ? "INFO_MANAGER" : user == null ? null : user.roleCode();
            if (user != null && user.status() == 1 && REVIEW_ROLES.contains(effectiveRole)) {
                request.setAttribute("reviewUserId", user.id());
                request.setAttribute("reviewRoleId", user.roleId());
                request.setAttribute("reviewRoleCode", effectiveRole);
                filterChain.doFilter(request, response);
                return;
            }
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ApiResponse<>(403, "无权查看审核报告", null));
    }
}
