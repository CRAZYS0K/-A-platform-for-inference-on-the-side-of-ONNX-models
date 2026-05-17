package com.sokolov.labs.gateway.web;

import com.sokolov.labs.gateway.backend.BackendClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/models")
public class ModelsPageController {

    private final BackendClient backend;

    public ModelsPageController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String list(Model view) {
        view.addAttribute("models", backend.listModels());
        return "models";
    }

    @PostMapping
    public String upload(@RequestParam String name,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes attrs) throws IOException {
        try {
            BackendClient.ModelDto saved = backend.uploadModel(name, file);
            attrs.addFlashAttribute("success", "Загружена модель: " + saved.name());
        } catch (RuntimeException ex) {
            attrs.addFlashAttribute("error", "Не удалось загрузить: " + ex.getMessage());
        }
        return "redirect:/models";
    }

    @PostMapping("/{id}/delete")
    public String delete(@org.springframework.web.bind.annotation.PathVariable UUID id, RedirectAttributes attrs) {
        backend.deleteModel(id);
        attrs.addFlashAttribute("success", "Модель удалена");
        return "redirect:/models";
    }
}
