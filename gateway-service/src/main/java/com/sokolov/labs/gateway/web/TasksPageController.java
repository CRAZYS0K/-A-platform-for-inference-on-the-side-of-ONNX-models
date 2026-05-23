package com.sokolov.labs.gateway.web;

import com.sokolov.labs.gateway.backend.BackendClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/tasks")
public class TasksPageController {

    private final BackendClient backend;

    public TasksPageController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String list(Model view) {
        view.addAttribute("tasks", backend.listTasks());
        view.addAttribute("models", backend.listModels());
        view.addAttribute("datasets", backend.listDatasets());
        return "tasks";
    }

    @GetMapping("/rows")
    public String rows(Model view) {
        view.addAttribute("tasks", backend.listTasks());
        return "tasks :: rows";
    }

    @PostMapping
    public String create(@RequestParam UUID modelId,
                         @RequestParam UUID datasetId,
                         RedirectAttributes attrs) {
        try {
            BackendClient.TaskDto task = backend.createTask(modelId, datasetId);
            attrs.addFlashAttribute("success", "Создана задача " + task.id());
        } catch (RuntimeException ex) {
            attrs.addFlashAttribute("error", "Не удалось создать: " + ex.getMessage());
        }
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@org.springframework.web.bind.annotation.PathVariable UUID id,
                         RedirectAttributes attrs) {
        try {
            BackendClient.TaskDto task = backend.cancelTask(id);
            attrs.addFlashAttribute("success", "Задача остановлена: статус " + task.status());
        } catch (RuntimeException ex) {
            attrs.addFlashAttribute("error", "Не удалось остановить: " + ex.getMessage());
        }
        return "redirect:/tasks";
    }
}
