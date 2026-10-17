package com.paqueteria.paqueteria_tracker.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SyncService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoClient mongoClient;

    private final Map<String, AtomicInteger> jobs = new ConcurrentHashMap<>();

    private static final List<String> TABLAS = List.of(
            "tb_clientes",
            "tb_contadores",
            "tb_tarifas",
            "tb_usuarios",
            "tb_lecturas",
            "tb_pagos"
    );

    public String startSync() {
        String jobId = UUID.randomUUID().toString();
        AtomicInteger progress = new AtomicInteger(0);
        jobs.put(jobId, progress);

        new Thread(() -> {
            try {
                MongoDatabase db = mongoClient.getDatabase("hidrogest_replica");
                int total = TABLAS.size();

                for (int i = 0; i < total; i++) {
                    String tabla = TABLAS.get(i);
                    sincronizarTabla(db, tabla);
                    progress.set((int) Math.round((i + 1) * 100.0 / total));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return jobId;
    }

    private void sincronizarTabla(MongoDatabase db, String tabla) {
        List<Map<String, Object>> filas = jdbcTemplate.queryForList("SELECT * FROM " + tabla);
        MongoCollection<Document> coleccion = db.getCollection(tabla);

        for (Map<String, Object> fila : filas) {
            Document doc = new Document();
            for (Map.Entry<String, Object> entry : fila.entrySet()) {
                Object valor = entry.getValue();
                if (valor instanceof java.math.BigInteger) {
                    valor = ((java.math.BigInteger) valor).longValue();
                } else if (valor instanceof java.math.BigDecimal) {
                    valor = ((java.math.BigDecimal) valor).doubleValue();
                }
                doc.append(entry.getKey(), valor);
            }
            coleccion.insertOne(doc);
        }
    }

    public Integer getProgress(String jobId) {
        AtomicInteger progress = jobs.get(jobId);
        return progress != null ? progress.get() : null;
    }
}