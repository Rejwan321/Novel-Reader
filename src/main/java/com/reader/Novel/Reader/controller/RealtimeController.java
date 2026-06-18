package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.SseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RealtimeController {

    @Autowired
    private SseService sseService;

    @GetMapping(value = "/api/realtime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.http.ResponseEntity<SseEmitter> stream(
            @RequestParam(required = false) Long chapterId,
            HttpSession session) {
        
        // Timeout set to 10 minutes (600,000 ms)
        SseEmitter emitter = new SseEmitter(600000L);
        
        User loggedInUser = (User) session.getAttribute("user");
        Long userId = loggedInUser != null ? loggedInUser.getId() : null;

        sseService.register(emitter, userId, chapterId);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Accel-Buffering", "no");
        headers.add("Cache-Control", "no-cache, no-transform");
        headers.add("Connection", "keep-alive");
        
        return new org.springframework.http.ResponseEntity<>(emitter, headers, org.springframework.http.HttpStatus.OK);
    }
}
