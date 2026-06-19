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

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
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
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }

        Long adminId = 1L;
        Long editorId = 3L;

        // 1. Seed Novel 1: Solo Leveling: Ragnarok
        if (!novelRepository.findAll().stream().anyMatch(n -> "Solo Leveling: Ragnarok".equalsIgnoreCase(n.getTitle()))) {
            Novel novel1 = new Novel(null, "Solo Leveling: Ragnarok", "Chugong",
                "In the shadows of the dimensional rift, a new crisis emerges. Sung Suho, the son of the Shadow Monarch Sung Jinwoo, has lived a normal life until the seals separating the dimensions began to crack. Now, the gates reopen, and the shadows call upon their new lord.",
                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                "NOVEL", "Action, Fantasy, System", 4.9, "ONGOING");
            novel1.setCreatorId(editorId);
            novel1.setYear(2024);
            novel1.setTags("Reincarnation, Overpowered, System, Male Lead, Magic");
            novel1.setCountryOfOrigin("Korea");
            novel1.setSource("Web Novel");
            novel1.setEditorSelection(true);
            novel1 = novelRepository.save(novel1);

            Chapter n1c1 = new Chapter(null, novel1, "The Son of the Monarch", 1.0,
                "<p>Sung Suho woke up to a ringing sound in his ears. It wasn't his alarm clock. It was a cold, mechanical voice echoing directly within his mind.</p>" +
                "<p><b>[System Alert: The Seals of the Shadow Realm are breaking.]</b></p>" +
                "<p>He looked at his hands, which were faintly glowing with a dark blue aura. The same color he had seen in his childhood dreams, where a man in dark armor fought countless monsters single-handedly. He never knew that the man was his father, nor did he know that the blood running in his veins carried the power to command death itself.</p>" +
                "<p>\"What is this?\" Suho muttered, standing up. As he took a step forward, the floor of his bedroom suddenly dissolved into black shadows. Out of the shadows, a small, black ant-like creature with tiny wings popped up, bowing its head.</p>" +
                "<p>\"Greetings, young master! We have waited ten thousand years for your awakening!\"</p>");
            chapterRepository.save(n1c1);

            Chapter n1c2 = new Chapter(null, novel1, "The Shadow Ant's Request", 2.0,
                "<p>Suho stared at the bowing shadow creature in absolute silence. \"Did you just... talk?\"</p>" +
                "<p>\"Yes, young master! I am a low-grade shadow soldier, left behind by the Great Monarch to guide you when the gates open again!\" the creature squeaked excitedly.</p>" +
                "<p>Suho rubbed his temples. He had a university exam in two hours. He was studying art history. He did not have time to be a shadow lord. \"Look, whatever your name is—\"</p>" +
                "<p>\"Call me Beru-junior, sire!\"</p>" +
                "<p>\"Beru-junior, I need to go to class. Can we do this shadow business later?\" Suho asked. But before the little ant could answer, a loud explosion rocked the entire building. The windows shattered, and a terrifying roar echoed from the streets below. A gate had opened right in the middle of Seoul.</p>",
                20);
            chapterRepository.save(n1c2);
        }

        // 2. Seed Novel 2: The Apothecary Diaries
        if (!novelRepository.findAll().stream().anyMatch(n -> "The Apothecary Diaries".equalsIgnoreCase(n.getTitle()))) {
            Novel novel2 = new Novel(null, "The Apothecary Diaries", "Natsu Hyūga",
                "Maomao, a young woman trained in the art of herbal medicine, is kidnapped and forced to work as a lowly maid in the Emperor's inner palace. Using her sharp wit and extensive knowledge of poisons, she solves mysteries in the court while trying to keep a low profile.",
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
                "NOVEL", "Mystery, Historical, Drama", 4.8, "COMPLETED");
            novel2.setCreatorId(editorId);
            novel2.setYear(2023);
            novel2.setTags("Female Lead, Smart Lead, Historical, Comedy");
            novel2.setCountryOfOrigin("Japan");
            novel2.setSource("Light Novel");
            novel2.setEditorSelection(true);
            novel2 = novelRepository.save(novel2);

            Chapter n2c1 = new Chapter(null, novel2, "The Poison Test", 1.0,
                "<p>The inner court was always filled with rumors, but the latest one was particularly grim. The infants of the Emperor's favorite consorts were dying one by one from a mysterious illness.</p>" +
                "<p>Maomao quietly washed the linens, keeping her head down. She knew exactly what was happening. It wasn't a curse, nor was it a assassin's poison. It was the expensive cosmetic powder the consorts used. The white lead was poisoning the babies through their mothers' skin.</p>" +
                "<p>She wanted to stay out of it. An apothecary in the inner palace was a dangerous thing to be. But when she saw Consort Gyokuyou carrying her crying baby, Maomao's conscience got the better of her. She wrote a warning on a piece of paper, wrapped it around a twig of azalea, and left it where the consort's guards would find it.</p>");
            chapterRepository.save(n2c1);

            Chapter n2c2 = new Chapter(null, novel2, "The Clever Maid", 2.0,
                "<p>Consort Gyokuyou's baby recovered, and it didn't take long for Jinshi, the eccentric and incredibly beautiful head eunuch, to trace the warning back to Maomao.</p>" +
                "<p>\"So, you are the one who knows about poison,\" Jinshi said, a beautiful but scheming smile on his face as he looked down at Maomao. He held a plate of sweets in front of her. \"Eat one.\"</p>" +
                "<p>Maomao didn't hesitate. She picked up a pastry and bit into it. She instantly tasted the subtle, bitter tang of blowfish poison. Instead of spitting it out or panicking, her eyes lit up with professional excitement. \"My, what a delightful dosage! It makes the tongue tingle just right!\"</p>" +
                "<p>Jinshi stared at her, half-impressed and half-horrified. \"You really are a strange girl. From today on, you are the Consort's official food taster.\"</p>",
                20);
            chapterRepository.save(n2c2);
        }

        // 3. Seed Comic 1: Tower of God
        if (!novelRepository.findAll().stream().anyMatch(n -> "Tower of God".equalsIgnoreCase(n.getTitle()))) {
            Novel comic1 = new Novel(null, "Tower of God", "SIU",
                "What do you desire? Money and wealth? Honor and pride? Authority and power? Revenge? Or something that transcends them all? Whatever you desire is here, at the top of the Tower. Join Bam on his climb to find his friend Rachel.",
                "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&auto=format&fit=crop&q=80",
                "COMIC", "Fantasy, Adventure, Action", 4.7, "ONGOING");
            comic1.setCreatorId(editorId);
            comic1.setYear(2020);
            comic1.setTags("Tower Climbing, Male Lead, Magic, Overpowered");
            comic1.setCountryOfOrigin("Korea");
            comic1.setSource("Original");
            comic1 = novelRepository.save(comic1);

            String comicImages1 = "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=800," +
                                  "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800," +
                                  "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800," +
                                  "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=800";
            Chapter c1c1 = new Chapter(null, comic1, "The First Floor Test", 1.0, comicImages1);
            chapterRepository.save(c1c1);

            String comicImages2 = "https://images.unsplash.com/photo-1604871000636-074fa5117945?w=800," +
                                  "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=800," +
                                  "https://images.unsplash.com/photo-1614850523459-c2f4c699c52e?w=800," +
                                  "https://images.unsplash.com/photo-1563089145-599997674d42?w=800";
            Chapter c1c2 = new Chapter(null, comic1, "The Black March Awakens", 2.0, comicImages2);
            chapterRepository.save(c1c2);
        }

        // 4. Seed Comic 2: Cyberpunk: Edgerunners
        if (!novelRepository.findAll().stream().anyMatch(n -> "Cyberpunk: Edgerunners".equalsIgnoreCase(n.getTitle()))) {
            Novel comic2 = new Novel(null, "Cyberpunk: Edgerunners", "Studio Trigger",
                "In a dystopia riddled with corruption and cybernetic implants, a talented but street-smart kid named David Martinez loses everything in a drive-by shooting. With nothing left to lose, he chooses to stay alive by becoming an edgerunner—a mercenary outlaw.",
                "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600&auto=format&fit=crop&q=80",
                "COMIC", "Sci-Fi, Cyberpunk, Action", 4.9, "COMPLETED");
            comic2.setCreatorId(editorId);
            comic2.setYear(2022);
            comic2.setTags("Cybernetics, Tragedy, Sci-Fi");
            comic2.setCountryOfOrigin("Japan");
            comic2.setSource("Original");
            comic2 = novelRepository.save(comic2);

            String comicImages3 = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800," +
                                  "https://images.unsplash.com/photo-1561715276-a2d087060f1d?w=800," +
                                  "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800";
            Chapter c2c1 = new Chapter(null, comic2, "I Want to Stay at Your House", 1.0, comicImages3);
            chapterRepository.save(c2c1);

            String comicImages4 = "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?w=800," +
                                  "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800," +
                                  "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=800";
            Chapter c2c2 = new Chapter(null, comic2, "Over the Edge", 2.0, comicImages4);
            chapterRepository.save(c2c2);
        }

        // 5. Seed Manga 1: Frieren: Beyond Journey's End
        if (!novelRepository.findAll().stream().anyMatch(n -> "Frieren: Beyond Journey's End".equalsIgnoreCase(n.getTitle()))) {
            Novel manga1 = new Novel(null, "Frieren: Beyond Journey's End", "Kanehito Yamada",
                "Elf mage Frieren and her fellow adventurers have defeated the Demon King and brought peace to the land. But Frieren, who lives much longer than humans, must watch her former companions age and pass away. Hoping to better understand humans, she embarks on a new journey.",
                "https://images.unsplash.com/photo-1601987177651-8edfe6c20009?w=600&auto=format&fit=crop&q=80",
                "MANGA", "Fantasy, Adventure, Slice of Life", 4.9, "ONGOING");
            manga1.setCreatorId(editorId);
            manga1.setYear(2023);
            manga1.setTags("Elves, Magic, Slow Pace, Female Lead");
            manga1.setCountryOfOrigin("Japan");
            manga1.setSource("Manga");
            manga1 = novelRepository.save(manga1);

            String mangaImages1 = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=800," +
                                  "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=800," +
                                  "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800";
            Chapter m1c1 = new Chapter(null, manga1, "The Journey's End", 1.0, mangaImages1);
            chapterRepository.save(m1c1);

            String mangaImages2 = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800," +
                                  "https://images.unsplash.com/photo-1561715276-a2d087060f1d?w=800," +
                                  "https://images.unsplash.com/photo-1604871000636-074fa5117945?w=800";
            Chapter m1c2 = new Chapter(null, manga1, "Priest Heiter's Request", 2.0, mangaImages2);
            chapterRepository.save(m1c2);
        }

        // 6. Seed Manga 2: Super Cub (with local cover URL path with accent characters)
        if (!novelRepository.findAll().stream().anyMatch(n -> "Super Cub".equalsIgnoreCase(n.getTitle()))) {
            if (isLocalDesktop) {
                Novel manga2 = new Novel(null, "Super Cub", "Tone Koken",
                    "it's about cub.",
                    "Zero Ts\u00fa.jpg",
                    "MANGA", "Slice of Life", 4.8, "ONGOING");
                manga2.setCreatorId(editorId);
                manga2.setYear(2021);
                manga2.setTags("Motorbikes, School Life, Female Lead");
                manga2.setCountryOfOrigin("Japan");
                manga2.setSource("Light Novel");
                manga2 = novelRepository.save(manga2);

                Chapter m2c1 = new Chapter(null, manga2, "xyz", 1.0, "xyz");
                chapterRepository.save(m2c1);

                Chapter m2c2 = new Chapter(null, manga2, "paid", 2.0, "it's paid content.", 10);
                chapterRepository.save(m2c2);
            }
        } else {
            // Ensure type is MANGA for existing Super Cub
            Novel superCub = novelRepository.findAll().stream()
                .filter(n -> "Super Cub".equalsIgnoreCase(n.getTitle()))
                .findFirst().orElse(null);
            if (superCub != null && !"MANGA".equals(superCub.getType())) {
                superCub.setType("MANGA");
                novelRepository.save(superCub);
            }
        }

        // Migrate/update creatorId for all existing novels to match the new roles
        for (Novel n : novelRepository.findAll()) {
            boolean updated = false;
            String title = n.getTitle();
            Long targetCreatorId = editorId;
            if (n.getCreatorId() == null || !n.getCreatorId().equals(targetCreatorId)) {
                n.setCreatorId(targetCreatorId);
                updated = true;
            }

            // Populate filter fields if null
            title = n.getTitle();
            if (title != null) {
                if (title.contains("Solo Leveling")) {
                    if (n.getYear() == null) { n.setYear(2024); updated = true; }
                    if (n.getTags() == null) { n.setTags("Reincarnation, Overpowered, System, Magic, Male Lead"); updated = true; }
                    if (n.getCountryOfOrigin() == null) { n.setCountryOfOrigin("Korea"); updated = true; }
                    if (n.getSource() == null) { n.setSource("Web Novel"); updated = true; }
                } else if (title.contains("Apothecary")) {
                    if (n.getYear() == null) { n.setYear(2023); updated = true; }
                    if (n.getTags() == null) { n.setTags("Female Lead, Smart Lead, Historical, Comedy"); updated = true; }
                    if (n.getCountryOfOrigin() == null) { n.setCountryOfOrigin("Japan"); updated = true; }
                    if (n.getSource() == null) { n.setSource("Light Novel"); updated = true; }
                } else if (title.contains("Super Cub")) {
                    if (n.getYear() == null) { n.setYear(2021); updated = true; }
                    if (n.getTags() == null) { n.setTags("Motorbikes, School Life, Female Lead"); updated = true; }
                    if (n.getCountryOfOrigin() == null) { n.setCountryOfOrigin("Japan"); updated = true; }
                    if (n.getSource() == null) { n.setSource("Light Novel"); updated = true; }
                }
            }

            if (updated) {
                novelRepository.save(n);
            }
        }

        // Seed 50 dummy novels for testing
        long currentCount = novelRepository.count();
        if (currentCount < 20) {
            System.out.println("Seeding 50 dummy novels...");
            for (int i = 1; i <= 50; i++) {
                Novel dummy = new Novel(null, "Test Novel " + i, "Author " + i,
                    "This is a dummy description for test novel " + i + " used for UI testing and verifying vertical scrolling layouts.",
                    "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                    "NOVEL", "Action, Adventure, Fantasy", 4.0 + (i % 10) * 0.1, "ONGOING");
                dummy.setCreatorId(editorId);
                dummy.setYear(2020 + (i % 7));
                dummy.setTags("Magic, Male Lead, Reincarnation");
                dummy.setCountryOfOrigin(i % 2 == 0 ? "Japan" : "Korea");
                dummy.setSource(i % 2 == 0 ? "Light Novel" : "Web Novel");
                novelRepository.save(dummy);
            }
            System.out.println("50 dummy novels seeded successfully!");
        }

        sanitizeLocalCoverUrls();
    }

    private void sanitizeLocalCoverUrls() {
        System.out.println("Sanitizing local cover URLs...");
        String userDir = System.getProperty("user.dir");
        for (Novel novel : novelRepository.findAll()) {
            String coverUrl = novel.getCoverUrl();
            if (coverUrl != null && !coverUrl.startsWith("http://") && !coverUrl.startsWith("https://") && !coverUrl.startsWith("/uploads/")) {
                Path matchedFile = findLocalFile(coverUrl, userDir);
                if (matchedFile != null) {
                    try {
                        String originalFilename = matchedFile.getFileName().toString();
                        String extension = "";
                        if (originalFilename.contains(".")) {
                            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                        }
                        String uniqueName = UUID.randomUUID().toString() + extension;
                        
                        Path srcDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads");
                        Path targetDir = Paths.get(userDir, "target", "classes", "static", "uploads");
                        
                        Files.createDirectories(srcDir);
                        Files.createDirectories(targetDir);
                        
                        Path srcFile = srcDir.resolve(uniqueName);
                        Path targetFile = targetDir.resolve(uniqueName);
                        
                        Files.copy(matchedFile, srcFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        Files.copy(matchedFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        
                        novel.setCoverUrl("/uploads/" + uniqueName);
                        novelRepository.save(novel);
                        System.out.println("Resolved local cover for novel '" + novel.getTitle() + "' to: /uploads/" + uniqueName);
                    } catch (Exception e) {
                        System.err.println("Error copying file: " + e.getMessage());
                    }
                }
            }
        }
    }

    private Path findLocalFile(String filename, String userDir) {
        try {
            Path directPath = Paths.get(filename);
            if (Files.exists(directPath) && !Files.isDirectory(directPath)) {
                return directPath;
            }
        } catch (Exception e) {}

        try {
            Path relativePath = Paths.get(userDir, filename);
            if (Files.exists(relativePath) && !Files.isDirectory(relativePath)) {
                return relativePath;
            }
        } catch (Exception e) {}

        try {
            String cleanName = Paths.get(filename).getFileName().toString().toLowerCase();
            File rootDir = new File(userDir);
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String name = f.getName().toLowerCase();
                        String normName = name.replaceAll("[ūúùûü]", "u");
                        String normClean = cleanName.replaceAll("[ūúùûü]", "u");
                        if (name.equals(cleanName) || normName.equals(normClean) || name.contains(cleanName) || cleanName.contains(name)) {
                            return f.toPath();
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return null;
    }
}
