package com.reader.Novel.Reader;

<<<<<<< HEAD
=======
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
>>>>>>> 6305f00 (Initial project upload)
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
<<<<<<< HEAD
public class NovelReaderApplication {

	public static void main(String[] args) {
		SpringApplication.run(NovelReaderApplication.class, args);
	}

=======
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
>>>>>>> 6305f00 (Initial project upload)
}
