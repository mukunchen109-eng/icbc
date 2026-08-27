package com.icbc.financialinfo.modules.task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class TaskRepository {
    private final JdbcTemplate jdbcTemplate;
    public TaskRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate=jdbcTemplate;
    }

    public List<TaskRecord> findAll() {
        String sql = """
                SELECT id,
                       DATE_FORMAT(target_date, '%Y-%m-%d') AS target_date,
                       trigger_type,
                       status,
                       processed_count,
                       retry_count,
                       message,
                       DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s') AS started_at,
                       DATE_FORMAT(finished_at, '%Y-%m-%d %H:%i:%s') AS finished_at
                FROM collection_job
                ORDER BY target_date DESC, id DESC
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new TaskRecord(
                resultSet.getLong("id"),
                resultSet.getString("target_date"),
                resultSet.getString("trigger_type"),
                resultSet.getString("status"),
                resultSet.getInt("processed_count"),
                resultSet.getInt("retry_count"),
                resultSet.getString("message"),
                resultSet.getString("started_at"),
                resultSet.getString("finished_at")
        ));
    }

    public record TaskRecord(
            Long id,
            String targetDate,
            String triggerType,
            String status,
            Integer processedCount,
            Integer retryCount,
            String message,
            String startedAt,
            String finishedAt
    ) {
    }

}
