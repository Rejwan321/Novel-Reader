package com.reader.Novel.Reader;

//import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
/*
public class NovelReaderApplication {

	public static void main(String[] args) {
	    ApplicationContext context = SpringApplication.run(NovelReaderApplication.class, args);
	}
 */

public class NovelReaderApplication implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${app.timezone:Asia/Kolkata}")
    private String appTimezone;

    @jakarta.annotation.PostConstruct
    public void init() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(appTimezone));
        System.out.println("Application timezone set to: " + java.util.TimeZone.getDefault().getID());
    }

    public static void main(String[] args) {
        System.setProperty("spring.h2.console.enabled", "true");
        SpringApplication.run(NovelReaderApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // User list console print removed for security purposes
    }
}
