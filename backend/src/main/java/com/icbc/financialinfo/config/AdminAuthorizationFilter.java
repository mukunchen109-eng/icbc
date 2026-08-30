package com.icbc.financialinfo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.financialinfo.common.ApiResponse;
import com.icbc.financialinfo.modules.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminAuthorizationFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;

  public AdminAuthorizationFilter(ObjectMapper objectMapper, UserRepository userRepository) {
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    String path = r.getRequestURI().substring(r.getContextPath().length());
    return (
      !(path.startsWith("/api/users") ||
        path.startsWith("/api/reports/admin/") ||
        path.startsWith("/api/admin/")) ||
      "OPTIONS".equals(r.getMethod())
    );
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest req,
    HttpServletResponse res,
    FilterChain chain
  ) throws ServletException, IOException {
    String authorization = req.getHeader("Authorization");
    String prefix = "Bearer dev-token-";
    if (authorization != null && authorization.startsWith(prefix)) {
      String username = authorization.substring(prefix.length());
      boolean isAdmin = userRepository
        .findByUsername(username)
        .map(user -> user.status() == 1 && "ADMIN".equals(user.roleCode()))
        .orElse(false);
      if (isAdmin) {
        chain.doFilter(req, res);
        return;
      }
    }
    res.setStatus(403);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
      res.getWriter(),
      new ApiResponse<>(403, "仅系统管理员可访问该功能", null)
    );
  }
}
