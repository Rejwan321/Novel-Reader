package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.NovelRepository;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import com.reader.Novel.Reader.model.FlakePackage;
import com.reader.Novel.Reader.repository.FlakePackageRepository;
import com.reader.Novel.Reader.util.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlakePackageRepository flakePackageRepository;

    @Override
    public void run(String... args) throws Exception {
        String userHome = System.getProperty("user.home");
        boolean isLocalDesktop = userHome != null && userHome.contains("sayan");

        if (flakePackageRepository.count() == 0) {
            flakePackageRepository.save(new FlakePackage(null, 100, 0.99));
            flakePackageRepository.save(new FlakePackage(null, 500, 3.99));
            flakePackageRepository.save(new FlakePackage(null, 1000, 6.99));
            flakePackageRepository.save(new FlakePackage(null, 2000, 11.99));
        }

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY = FALSE");
        try {
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS DECRYPT_PASSWORD FOR 'com.reader.Novel.Reader.util.PasswordUtils.decryptForH2'");
            
            // Ensure comments table has reports_count column
            jdbcTemplate.execute("ALTER TABLE comments ADD COLUMN IF NOT EXISTS reports_count INT DEFAULT 0 NOT NULL");

            // Ensure notifications table allows NULL comment_id for chapter publish updates
            jdbcTemplate.execute("ALTER TABLE notifications ALTER COLUMN comment_id DROP NOT NULL");

            // Re-map any references of the legacy editor (ID 2) to the admin (ID 1)
            jdbcTemplate.update("UPDATE novels SET creator_id = 1 WHERE creator_id = 2");
            jdbcTemplate.update("DELETE FROM bookmarks WHERE user_id = 2 AND novel_id IN (SELECT novel_id FROM bookmarks WHERE user_id = 1)");
            jdbcTemplate.update("UPDATE bookmarks SET user_id = 1 WHERE user_id = 2");
            jdbcTemplate.update("DELETE FROM purchases WHERE user_id = 2 AND chapter_id IN (SELECT chapter_id FROM purchases WHERE user_id = 1)");
            jdbcTemplate.update("UPDATE purchases SET user_id = 1 WHERE user_id = 2");
            jdbcTemplate.update("DELETE FROM ratings WHERE user_id = 2 AND novel_id IN (SELECT novel_id FROM ratings WHERE user_id = 1)");
            jdbcTemplate.update("UPDATE ratings SET user_id = 1 WHERE user_id = 2");

            // Clean up Slice Of Life capitalization inconsistency
            jdbcTemplate.update("UPDATE novels SET genre = REPLACE(genre, 'Slice Of Life', 'Slice of Life')");

            // Delete legacy users
            jdbcTemplate.execute("DELETE FROM reader_internal WHERE email IN ('sakura@sakura.com', 'editor@yuki.com')");

            // One-time database migration to default existing users to subscribed to updates
            java.util.List<java.util.Map<String, Object>> migrationSetting = jdbcTemplate.queryForList("SELECT setting_value FROM system_settings WHERE setting_key = 'email_migration_done'");
            if (migrationSetting.isEmpty()) {
                jdbcTemplate.execute("UPDATE reader_internal SET subscribed_to_updates = TRUE");
                jdbcTemplate.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('email_migration_done', 'true')");
                System.out.println("One-time email subscription migration executed successfully.");
            }
            
            // Ensure owner exists with ID 0
            java.util.List<java.util.Map<String, Object>> owners = jdbcTemplate.queryForList("SELECT id FROM reader_internal WHERE email = 'sakura'");
            String sakuraHashed = PasswordUtils.hashPassword("sakura");
            if (owners.isEmpty()) {
                jdbcTemplate.update("INSERT INTO reader_internal (id, name, email, password, user_type, balance) VALUES (0, 'System Owner', 'sakura', ?, 'OWNER', 100)", sakuraHashed);
            } else {
                Long currentOwnerId = ((Number) owners.get(0).get("id")).longValue();
                if (currentOwnerId != 0L) {
                    jdbcTemplate.update("UPDATE reader_internal SET id = 0 WHERE id = ?", currentOwnerId);
                }
                jdbcTemplate.update("UPDATE reader_internal SET user_type = 'OWNER', name = 'System Owner', password = ? WHERE email = 'sakura'", sakuraHashed);
            }

            // Ensure admin exists with ID 1
            java.util.List<java.util.Map<String, Object>> admins = jdbcTemplate.queryForList("SELECT id FROM reader_internal WHERE email = 'admin'");
            String adminHashed = PasswordUtils.hashPassword("admin");
            if (admins.isEmpty()) {
                jdbcTemplate.update("INSERT INTO reader_internal (id, name, email, password, user_type, balance) VALUES (1, 'System Admin', 'admin', ?, 'ADMIN', 100)", adminHashed);
            } else {
                Long currentAdminId = ((Number) admins.get(0).get("id")).longValue();
                if (currentAdminId != 1L) {
                    jdbcTemplate.update("UPDATE reader_internal SET id = 1 WHERE id = ?", currentAdminId);
                }
                jdbcTemplate.update("UPDATE reader_internal SET user_type = 'ADMIN', name = 'System Admin', password = ? WHERE email = 'admin'", adminHashed);
            }

            // Ensure editor exists with ID 3
            java.util.List<java.util.Map<String, Object>> editorsList = jdbcTemplate.queryForList("SELECT id FROM reader_internal WHERE email = 'editor'");
            String editorHashed = PasswordUtils.hashPassword("editor");
            if (editorsList.isEmpty()) {
                jdbcTemplate.update("INSERT INTO reader_internal (id, name, email, password, user_type, balance) VALUES (3, 'System Translator', 'editor', ?, 'EDITOR', 100)", editorHashed);
            } else {
                Long currentEditorId = ((Number) editorsList.get(0).get("id")).longValue();
                if (currentEditorId != 3L) {
                    jdbcTemplate.update("UPDATE reader_internal SET id = 3 WHERE id = ?", currentEditorId);
                }
                jdbcTemplate.update("UPDATE reader_internal SET user_type = 'EDITOR', name = 'System Translator', password = ? WHERE email = 'editor'", editorHashed);
            }

            // Create the view named READER for H2 console users
            jdbcTemplate.execute("DROP TABLE IF EXISTS READER CASCADE");
            jdbcTemplate.execute("DROP VIEW IF EXISTS READER");
            jdbcTemplate.execute("CREATE VIEW READER AS SELECT id, balance, email, name, DECRYPT_PASSWORD(password) AS password, user_type FROM reader_internal");

            // Create the H2 database user admin/admin with admin privileges
            jdbcTemplate.execute("CREATE USER IF NOT EXISTS admin PASSWORD 'admin'");
            jdbcTemplate.execute("ALTER USER admin ADMIN TRUE");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY = TRUE");
        }

        Long editorId = 3L;

        // Migrate/update creatorId for all existing novels to match the new roles
        for (Novel n : novelRepository.findAll()) {
            boolean updated = false;
            Long targetCreatorId = editorId;
            if (n.getCreatorId() == null || !n.getCreatorId().equals(targetCreatorId)) {
                n.setCreatorId(targetCreatorId);
                updated = true;
            }
            if (updated) {
                novelRepository.save(n);
            }
        }
    }
}
