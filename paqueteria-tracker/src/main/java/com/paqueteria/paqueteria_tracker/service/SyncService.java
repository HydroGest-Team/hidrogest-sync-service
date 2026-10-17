package com.paqueteria.paqueteria_tracker.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SyncService {

    private final Map<String, AtomicInteger> jobs = new ConcurrentHashMap<>();

    public String startSync() {
        String jobId = UUID.randomUUID().toString();
        AtomicInteger progress = new AtomicInteger(0);
        jobs.put(jobId, progress);

        new Thread(() -> {
            try {
                for (int i = 10; i <= 100; i += 10) {
                    Thread.sleep(1000);
                    progress.set(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return jobId;
    }

    public Integer getProgress(String jobId) {
        AtomicInteger progress = jobs.get(jobId);
        return progress != null ? progress.get() : null;
    }
}