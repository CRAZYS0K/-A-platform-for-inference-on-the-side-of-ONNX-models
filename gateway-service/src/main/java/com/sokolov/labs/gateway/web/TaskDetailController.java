package com.sokolov.labs.gateway.web;

import com.sokolov.labs.gateway.backend.BackendClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

import java.util.UUID;

@Controller
@RequestMapping("/tasks/{id}")
public class TaskDetailController {

    private final BackendClient backend;

    public TaskDetailController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String page(@PathVariable UUID id, Model view) {
        BackendClient.TaskDto task = backend.getTask(id);
        view.addAttribute("task", task);
        return "task-detail";
    }

    @GetMapping("/results.json")
    @ResponseBody
    public ResponseEntity<byte[]> results(@PathVariable UUID id) {
        byte[] body = backend.taskResultsJson(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/images/**")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable UUID id, HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String marker = "/images/";
        int idx = fullPath.indexOf(marker);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relative = fullPath.substring(idx + marker.length());
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        BackendClient.ImagePayload payload = backend.taskImage(id, relative);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(payload.bytes());
    }
}
