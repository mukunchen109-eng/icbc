package com.icbc.financialinfo.modules.report.repository;

import com.icbc.financialinfo.modules.report.model.NewsPoolRecord;
import com.icbc.financialinfo.modules.report.properties.ReportProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

@Repository
public class NewsPoolRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties reportProperties;

    public NewsPoolRepository(JdbcTemplate jdbcTemplate, ReportProperties reportProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportProperties = reportProperties;
    }

    public List<NewsPoolRecord> findByNewsDate(String newsDate) {
        String tableName = resolveTableName();
        String sql = "select news_date, title, content, industry, area, content_hash from " + tableName + " where news_date = ? order by title";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new NewsPoolRecord(
                        rs.getString("news_date"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("industry"),
                        rs.getString("area"),
                        rs.getString("content_hash")
                ),
                newsDate
        );
    }

    private String resolveTableName() {
        String tableName = reportProperties.getNewsTable();
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalStateException("app.report.news-table 配置非法");
        }
        return tableName;
    }
}
