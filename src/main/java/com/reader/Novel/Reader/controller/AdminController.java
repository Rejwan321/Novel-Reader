package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.UserService;
import com.reader.Novel.Reader.service.NovelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private NovelService novelService;

    @GetMapping("/admin")
    public String adminDashboard(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return "redirect:/?showLogin=true";
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return "redirect:/"; // Unauthorized, kick out to homepage
        }

        boolean secured = novelService.isSecuredMode();
        model.addAttribute("securedMode", secured);

        // If secured mode is active and user is NOT owner, filter out all data!
        boolean isOwner = "OWNER".equals(role);
        if (secured && !isOwner) {
            model.addAttribute("users", java.util.Collections.emptyList());
            model.addAttribute("novels", java.util.Collections.emptyList());
            model.addAttribute("userRole", role);
            return "admin";
        }

        // Admin-only data (or owner)
        if ("ADMIN".equals(role) || "OWNER".equals(role)) {
            java.util.List<User> filteredUsers = userService.getUsers().stream()
                .filter(u -> !"OWNER".equals(u.getUser_type()))
                .collect(java.util.stream.Collectors.toList());
            model.addAttribute("users", filteredUsers);
        }

        // Shared admin/editor data
        if ("EDITOR".equals(role)) {
            model.addAttribute("novels", novelService.getNovelsByCreatorId(loggedInUser.getId()));
        } else {
            model.addAttribute("novels", novelService.getAllNovels());
        }
        model.addAttribute("userRole", role);

        return "admin";
    }
}
