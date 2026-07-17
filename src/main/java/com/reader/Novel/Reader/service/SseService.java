package com.reader.Novel.Reader.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SseService {
    private static final Logger logger = LoggerFactory.getLogger(SseService.class);

    // Mappings of active emitters by userId
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // Mappings of active emitters by chapterId
    private final Map<Long, List<SseEmitter>> chapterEmitters = new ConcurrentHashMap<>();

    // Global registry of all connected emitters
    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    public void register(SseEmitter emitter, Long userId, Long chapterId) {
        if (userId != null) {
            userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        }
        if (chapterId != null) {
            chapterEmitters.computeIfAbsent(chapterId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        }
        globalEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter, userId, chapterId));
        emitter.onTimeout(() -> removeEmitter(emitter, userId, chapterId));
        emitter.onError((e) -> removeEmitter(emitter, userId, chapterId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("Real-time stream established"));
        } catch (IOException e) {
            removeEmitter(emitter, userId, chapterId);
        }
    }

    private void removeEmitter(SseEmitter emitter, Long userId, Long chapterId) {
        globalEmitters.remove(emitter);
        if (userId != null) {
            List<SseEmitter> list = userEmitters.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    userEmitters.remove(userId);
                }
            }
        } else {
            userEmitters.forEach((id, list) -> {
                if (list.remove(emitter)) {
                    if (list.isEmpty()) {
                        userEmitters.remove(id);
                    }
                }
            });
        }
        if (chapterId != null) {
            List<SseEmitter> list = chapterEmitters.get(chapterId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    chapterEmitters.remove(chapterId);
                }
            }
        } else {
            chapterEmitters.forEach((id, list) -> {
                if (list.remove(emitter)) {
                    if (list.isEmpty()) {
                        chapterEmitters.remove(id);
                    }
                }
            });
        }
    }

    public void sendNotification(Long userId, Object notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            List<SseEmitter> failed = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("notification_received").data(notification));
                } catch (Exception e) {
                    failed.add(emitter);
                }
            }
            for (SseEmitter f : failed) {
                removeEmitter(f, userId, null);
            }
        }
    }

    public void sendCommentEvent(Long chapterId, String eventType, Object data) {
        List<SseEmitter> emitters = chapterEmitters.get(chapterId);
        if (emitters != null) {
            List<SseEmitter> failed = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name(eventType).data(data));
                } catch (Exception e) {
                    failed.add(emitter);
                }
            }
            for (SseEmitter f : failed) {
                removeEmitter(f, null, chapterId);
            }
        }
    }

    public void sendGlobalEvent(String eventType, Object data) {
        List<SseEmitter> failed = new ArrayList<>();
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (Exception e) {
                failed.add(emitter);
            }
        }
        for (SseEmitter f : failed) {
            removeEmitter(f, null, null);
        }
    }

    @Scheduled(fixedRate = 15000) // Send a ping every 15 seconds
    public void sendHeartbeat() {
        List<SseEmitter> failed = new ArrayList<>();
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                failed.add(emitter);
            }
        }
        for (SseEmitter f : failed) {
            removeEmitter(f, null, null);
        }
    }
}
