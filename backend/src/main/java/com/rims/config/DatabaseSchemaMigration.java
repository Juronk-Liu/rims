package com.rims.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Adds fields required by the current release to databases created by earlier releases.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        boolean reservationColumnAdded = addColumnIfMissing(
                "budget_item", "reserved_amount", "DECIMAL(14, 2) NOT NULL DEFAULT 0");
        addColumnIfMissing("project", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("expense", "version", "BIGINT NOT NULL DEFAULT 0");

        if (reservationColumnAdded) {
            jdbcTemplate.update("""
                    UPDATE budget_item
                    SET reserved_amount = COALESCE((
                        SELECT SUM(e.amount)
                        FROM expense e
                        WHERE e.project_id = budget_item.project_id
                          AND e.category = budget_item.category
                          AND e.status IN ('APPROVED', 'PENDING')
                    ), 0)
                    """);
        }
    }

    private boolean addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (columnExists(table, column)) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        return true;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String tableName : caseVariants(table)) {
                for (String columnName : caseVariants(column)) {
                    try (ResultSet columns = metadata.getColumns(catalog, null, tableName, columnName)) {
                        if (columns.next()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String[] caseVariants(String value) {
        return new String[]{value, value.toUpperCase(Locale.ROOT), value.toLowerCase(Locale.ROOT)};
    }
}
