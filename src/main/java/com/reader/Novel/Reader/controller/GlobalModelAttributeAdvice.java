package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.FlakePackage;
import com.reader.Novel.Reader.service.NovelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalModelAttributeAdvice {

    @Autowired
    private NovelService novelService;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @ModelAttribute("flakePackages")
    public List<FlakePackage> addFlakePackages() {
        return novelService.getAllFlakePackages();
    }

    @ModelAttribute("googleClientId")
    public String addGoogleClientId() {
        return systemSettingRepository.findById("google.client_id")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("your-google-client-id");
    }

    @ModelAttribute("discordClientId")
    public String addDiscordClientId() {
        return systemSettingRepository.findById("discord.client_id")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("");
    }

    @ModelAttribute("googleEnabled")
    public boolean addGoogleEnabled() {
        return systemSettingRepository.findById("google.enabled")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    @ModelAttribute("discordEnabled")
    public boolean addDiscordEnabled() {
        return systemSettingRepository.findById("discord.enabled")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    @ModelAttribute("uiTemplate")
    public String addUiTemplate() {
        return systemSettingRepository.findById("ui.template")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("modern");
    }

    @ModelAttribute("uiTheme")
    public String addUiTheme() {
        return systemSettingRepository.findById("ui.theme")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("midnight");
    }

    @ModelAttribute("uiColorPrimary")
    public String addUiColorPrimary() {
        return systemSettingRepository.findById("ui.color.primary")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("#5e63b6");
    }

    @ModelAttribute("uiColorBg")
    public String addUiColorBg() {
        return systemSettingRepository.findById("ui.color.bg")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("#0f0f1a");
    }

    @ModelAttribute("uiColorCard")
    public String addUiColorCard() {
        return systemSettingRepository.findById("ui.color.card")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("#181829");
    }

    @ModelAttribute("uiFontPrimary")
    public String addUiFontPrimary() {
        return systemSettingRepository.findById("ui.font.primary")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("Poppins");
    }

    @org.springframework.beans.factory.annotation.Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @org.springframework.beans.factory.annotation.Value("${firebase.api-key:}")
    private String firebaseApiKey;

    @org.springframework.beans.factory.annotation.Value("${firebase.auth-domain:}")
    private String firebaseAuthDomain;

    @org.springframework.beans.factory.annotation.Value("${firebase.project-id:}")
    private String firebaseProjectId;

    @org.springframework.beans.factory.annotation.Value("${firebase.storage-bucket:}")
    private String firebaseStorageBucket;

    @org.springframework.beans.factory.annotation.Value("${firebase.messaging-sender-id:}")
    private String firebaseMessagingSenderId;

    @org.springframework.beans.factory.annotation.Value("${firebase.app-id:}")
    private String firebaseAppId;

    @ModelAttribute("firebaseEnabled")
    public boolean addFirebaseEnabled() {
        return firebaseEnabled;
    }

    @ModelAttribute("firebaseConfig")
    public java.util.Map<String, String> addFirebaseConfig() {
        return java.util.Map.of(
            "apiKey", firebaseApiKey != null ? firebaseApiKey : "",
            "authDomain", firebaseAuthDomain != null ? firebaseAuthDomain : "",
            "projectId", firebaseProjectId != null ? firebaseProjectId : "",
            "storageBucket", firebaseStorageBucket != null ? firebaseStorageBucket : "",
            "messagingSenderId", firebaseMessagingSenderId != null ? firebaseMessagingSenderId : "",
            "appId", firebaseAppId != null ? firebaseAppId : ""
        );
    }
}
