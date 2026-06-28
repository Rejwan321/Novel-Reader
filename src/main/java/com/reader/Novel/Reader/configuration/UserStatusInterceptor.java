package com.reader.Novel.Reader.configuration;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class UserStatusInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null) {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser != null && sessionUser.getId() != null) {
                User dbUser = userService.getUserById(sessionUser.getId());
                if (dbUser != null && dbUser.getId() != null) {
                    boolean isBanned = Boolean.TRUE.equals(dbUser.getBanned());
                    boolean isTimedOut = dbUser.getTimeoutUntil() != null && dbUser.getTimeoutUntil().isAfter(LocalDateTime.now());
                    
                    if (isBanned || isTimedOut) {
                        session.invalidate();
                        String errorMsg = isBanned ? "Your account has been banned." : "Your account is temporarily timed out.";
                        
                        String uri = request.getRequestURI();
                        if (uri.startsWith("/api/")) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            PrintWriter writer = response.getWriter();
                            writer.write("{\"error\":\"" + errorMsg + "\"}");
                            writer.flush();
                            return false;
                        } else {
                            response.sendRedirect("/?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
