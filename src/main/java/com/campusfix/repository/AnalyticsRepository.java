package com.campusfix.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic analytics for the admin dashboard, written with plain JdbcTemplate
 * SQL rather than JPA — a couple of dashboards on a project like this are
 * simple enough to hand-write, and it keeps the JDBC layer mentioned in the
 * stack actually doing something in the codebase.
 */
@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Long> countByStatus() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM complaints GROUP BY status";
        Map<String, Long> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("status"), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    public Map<String, Long> countByCategory() {
        String sql = "SELECT category, COUNT(*) AS cnt FROM complaints GROUP BY category";
        Map<String, Long> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("category"), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    public Double averageFeedbackRating() {
        String sql = "SELECT AVG(feedback_rating) AS avg_rating FROM complaints WHERE feedback_rating IS NOT NULL";
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    public Double averageResolutionHours() {
        String sql = "SELECT AVG(TIMESTAMPDIFF(HOUR, created_at, resolved_at)) AS avg_hours " +
                     "FROM complaints WHERE resolved_at IS NOT NULL";
        try {
            return jdbcTemplate.queryForObject(sql, Double.class);
        } catch (Exception e) {
            // TIMESTAMPDIFF is MySQL-specific; fall back gracefully on H2/tests.
            return null;
        }
    }

    public long countUnassigned() {
        String sql = "SELECT COUNT(*) FROM complaints WHERE assigned_staff_id IS NULL AND status NOT IN ('VERIFIED','REJECTED','CANCELLED')";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    public long countRecurringIssues() {
        String sql = "SELECT COUNT(*) FROM complaints WHERE recurrence_count > 0";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }
}
