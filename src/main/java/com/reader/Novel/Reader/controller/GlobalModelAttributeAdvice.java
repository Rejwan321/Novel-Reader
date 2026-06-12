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

    @ModelAttribute("flakePackages")
    public List<FlakePackage> addFlakePackages() {
        return novelService.getAllFlakePackages();
    }
}
