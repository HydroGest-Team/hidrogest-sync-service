package com.paqueteria.paqueteria_tracker.controller;

import com.paqueteria.paqueteria_tracker.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<?> startSync() {
        String jobId = syncService.startSync();
        if (jobId == null) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Ya hay una sincronización en curso"));
        }
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String jobId) {
        Integer progress = syncService.getProgress(jobId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("jobId", jobId, "progress", progress));
    }
}