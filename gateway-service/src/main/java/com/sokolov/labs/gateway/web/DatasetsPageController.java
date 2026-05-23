package com.sokolov.labs.gateway.web;

import com.sokolov.labs.gateway.backend.BackendClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/datasets")
public class DatasetsPageController {

    private final BackendClient backend;

    public DatasetsPageController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String list(Model view) {
        view.addAttribute("datasets", backend.listDatasets());
        return "datasets";
    }

    @PostMapping
    public String upload(@RequestParam String name,
                         @RequestParam(required = false, defaultValue = "UNLABELED") String kind,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes attrs) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            attrs.addFlashAttribute("error", "Файл должен иметь расширение .zip");
            return "redirect:/datasets";
        }
        if (file.isEmpty()) {
            attrs.addFlashAttribute("error", "Файл пустой");
            return "redirect:/datasets";
        }
        try {
            BackendClient.DatasetDto saved = backend.uploadDataset(name, kind, file);
            attrs.addFlashAttribute("success", "Загружен датасет: " + saved.name());
        } catch (RuntimeException ex) {
            attrs.addFlashAttribute("error", "Не удалось загрузить: " + ex.getMessage());
        }
        return "redirect:/datasets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes attrs) {
        backend.deleteDataset(id);
        attrs.addFlashAttribute("success", "Датасет удалён");
        return "redirect:/datasets";
    }
}
