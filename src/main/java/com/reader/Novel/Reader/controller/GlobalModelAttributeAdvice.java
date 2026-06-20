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
}
