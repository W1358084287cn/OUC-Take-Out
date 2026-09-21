package edu.ouc.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbSchemaConfig implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DbSchemaConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN address_book_id BIGINT DEFAULT NULL");
            jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN phone VARCHAR(50)");
            jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN address VARCHAR(200)");
            jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN consignee VARCHAR(50)");
        } catch (Exception e) {
            System.err.println("数据库字段扩展警告（可忽略）: " + e.getMessage());
        }
    }
}