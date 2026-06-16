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
/*
public class NovelReaderApplication {

	public static void main(String[] args) {
	    ApplicationContext context = SpringApplication.run(NovelReaderApplication.class, args);
	}
 */

public class NovelReaderApplication implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    public static void main(String[] args) {
        SpringApplication.run(NovelReaderApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("🔍 Fetching all users from the database:");
        userRepository.findAll().forEach(System.out::println);
    }
}
