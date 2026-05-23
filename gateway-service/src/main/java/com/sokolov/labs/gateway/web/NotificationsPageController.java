package com.sokolov.labs.gateway.web;

import com.sokolov.labs.gateway.backend.BackendClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notifications")
public class NotificationsPageController {

    private final BackendClient backend;

    public NotificationsPageController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String page(Model view) {
        view.addAttribute("prefs", backend.getNotificationPrefs());
        return "notifications";
    }

    @PostMapping
    public String update(@RequestParam(defaultValue = "false") boolean emailEnabled,
                         RedirectAttributes attrs) {
        try {
            backend.updateNotificationPrefs(emailEnabled, false, "");
            attrs.addFlashAttribute("success", "Настройки сохранены");
        } catch (RuntimeException ex) {
            attrs.addFlashAttribute("error", "Не удалось сохранить: " + ex.getMessage());
        }
        return "redirect:/notifications";
    }
}
