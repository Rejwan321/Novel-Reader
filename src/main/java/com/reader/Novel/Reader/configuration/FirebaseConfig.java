package com.reader.Novel.Reader.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Value("${firebase.service-account.path:./firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void init() {
        if (!firebaseEnabled) {
            System.out.println("Firebase Auth is disabled in properties.");
            return;
        }

        try {
            File serviceAccountFile = new File(serviceAccountPath);
            if (!serviceAccountFile.exists()) {
                System.err.println("Firebase Service Account key file not found at: " + serviceAccountFile.getAbsolutePath());
                System.err.println("Firebase Auth won't be initialized!");
                return;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream(serviceAccountFile);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("Firebase App initialized successfully from: " + serviceAccountFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error initializing Firebase App: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
