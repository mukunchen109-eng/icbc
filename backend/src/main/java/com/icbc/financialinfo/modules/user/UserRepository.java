package com.icbc.financialinfo.modules.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private static final String SELECT_COLUMNS = """
    SELECT u.id, u.username, u.password_hash, u.role_id, u.status,
           DATE_FORMAT(u.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
           r.role_code, r.role_name
      FROM sys_user u
      JOIN sys_role r ON r.id = u.role_id
    """;
  private static final RowMapper<UserAccount> USER_MAPPER = (rs, rowNum) ->
    new UserAccount(
      rs.getLong("id"),
      rs.getString("username"),
      rs.getString("password_hash"),
      rs.getLong("role_id"),
      rs.getInt("status"),
      rs.getString("created_at"),
      rs.getString("role_code"),
      rs.getString("role_name")
    );

  private final JdbcTemplate jdbcTemplate;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UserAccount> findByUsername(String username) {
    return jdbcTemplate
      .query(SELECT_COLUMNS + " WHERE u.username = ?", USER_MAPPER, username)
      .stream()
      .findFirst();
  }

  public Optional<UserAccount> findById(long id) {
    return jdbcTemplate
      .query(SELECT_COLUMNS + " WHERE u.id = ?", USER_MAPPER, id)
      .stream()
      .findFirst();
  }

  public List<UserAccount> findPage(
    int pageNum,
    int pageSize,
    String username,
    Long roleId,
    Integer status
  ) {
    QueryParts query = filters(username, roleId, status);
    query.parameters().add(pageSize);
    query.parameters().add((pageNum - 1) * pageSize);
    return jdbcTemplate.query(
      SELECT_COLUMNS + query.whereClause() + " ORDER BY u.id LIMIT ? OFFSET ?",
      USER_MAPPER,
      query.parameters().toArray()
    );
  }

  public long count(String username, Long roleId, Integer status) {
    QueryParts query = filters(username, roleId, status);
    Long count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sys_user u" + query.whereClause(),
      Long.class,
      query.parameters().toArray()
    );
    return count == null ? 0 : count;
  }

  public boolean usernameExists(String username) {
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sys_user WHERE LOWER(username) = LOWER(?)",
      Integer.class,
      username
    );
    return count != null && count > 0;
  }

  public boolean roleExists(long roleId) {
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sys_role WHERE id = ?",
      Integer.class,
      roleId
    );
    return count != null && count > 0;
  }

  public long create(String username, String passwordHash, long roleId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
      connection -> {
        PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO sys_user(username,password_hash,role_id,status,created_at) " +
            "VALUES (?,?,?,1,CURRENT_TIMESTAMP)",
          Statement.RETURN_GENERATED_KEYS
        );
        statement.setString(1, username);
        statement.setString(2, passwordHash);
        statement.setLong(3, roleId);
        return statement;
      },
      keyHolder
    );
    if (keyHolder.getKey() == null) throw new IllegalStateException("数据库未返回用户ID");
    return keyHolder.getKey().longValue();
  }

  public boolean update(long id, long roleId, int status) {
    return (
      jdbcTemplate.update(
        "UPDATE sys_user SET role_id = ?, status = ? WHERE id = ?",
        roleId,
        status,
        id
      ) >
      0
    );
  }

  private QueryParts filters(String username, Long roleId, Integer status) {
    List<String> conditions = new ArrayList<>();
    List<Object> parameters = new ArrayList<>();
    if (username != null && !username.isBlank()) {
      conditions.add("LOWER(u.username) LIKE ?");
      parameters.add("%" + username.trim().toLowerCase() + "%");
    }
    if (roleId != null) {
      conditions.add("u.role_id = ?");
      parameters.add(roleId);
    }
    if (status != null) {
      conditions.add("u.status = ?");
      parameters.add(status);
    }
    String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    return new QueryParts(where, parameters);
  }

  public record UserAccount(
    Long id,
    String username,
    String passwordHash,
    Long roleId,
    Integer status,
    String createdAt,
    String roleCode,
    String roleName
  ) {}

  private record QueryParts(String whereClause, List<Object> parameters) {}
}
