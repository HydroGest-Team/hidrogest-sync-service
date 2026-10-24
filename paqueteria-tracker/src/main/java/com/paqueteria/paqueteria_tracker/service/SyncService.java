package com.paqueteria.paqueteria_tracker.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.slf4j.Logger;                          // ← NUEVO
import org.slf4j.LoggerFactory;                   // ← NUEVO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class); // ← NUEVO

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoClient mongoClient;

    private final Map<String, AtomicInteger> jobs = new ConcurrentHashMap<>();
    private final AtomicBoolean syncEnCurso = new AtomicBoolean(false);

    private static final List<String> TABLAS = List.of(
            "tb_clientes", "tb_contadores", "tb_tarifas",
            "tb_usuarios", "tb_lecturas", "tb_pagos"
    );

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String startSync() {
        if (!syncEnCurso.compareAndSet(false, true)) {
            return null;
        }
        String jobId = UUID.randomUUID().toString();
        AtomicInteger progress = new AtomicInteger(0);
        jobs.put(jobId, progress);

        new Thread(() -> {
            try {
                MongoDatabase db = mongoClient.getDatabase("hidrogest_replica");
                LocalDateTime desdeWhen = obtenerUltimoSync(db);
                LocalDateTime ahora = LocalDateTime.now();

                log.info("[Sync] Iniciando sincronización. jobId: {}", jobId);  // ← NUEVO
                log.info("[Sync] Último sync registrado: {}", desdeWhen);       // ← NUEVO

                int total = TABLAS.size();
                for (int i = 0; i < total; i++) {
                    sincronizarTabla(db, TABLAS.get(i), desdeWhen);
                    progress.set((int) Math.round((i + 1) * 100.0 / total));
                }

                guardarUltimoSync(db, ahora);
                log.info("[Sync] Sincronización completada correctamente.");     // ← NUEVO
            } catch (Exception e) {
                log.error("[Sync] Error durante la sincronización: {}", e.getMessage(), e); // ← NUEVO
                e.printStackTrace();
            } finally {
                syncEnCurso.set(false);
            }
        }).start();

        return jobId;
    }

    private LocalDateTime obtenerUltimoSync(MongoDatabase db) {
        Document meta = db.getCollection("sync_metadata")
                .find(Filters.eq("_id", "last_sync")).first();
        if (meta == null || meta.getString("timestamp") == null) {
            return LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        }
        return LocalDateTime.parse(meta.getString("timestamp"), FMT);
    }

    private void guardarUltimoSync(MongoDatabase db, LocalDateTime timestamp) {
        Document meta = new Document("_id", "last_sync")
                .append("timestamp", timestamp.format(FMT));
        db.getCollection("sync_metadata")
                .replaceOne(Filters.eq("_id", "last_sync"), meta,
                        new ReplaceOptions().upsert(true));
    }

    private void sincronizarTabla(MongoDatabase db, String tabla, LocalDateTime desde) {
        String sql = "SELECT * FROM " + tabla + " WHERE updated_at > ?";
        List<Map<String, Object>> filas = jdbcTemplate.queryForList(sql, desde);

        if (filas.isEmpty()) {
            log.info("[Sync] {} → sin cambios", tabla);   // ← NUEVO
            return;
        }

        log.info("[Sync] {} → {} registro(s) nuevo(s)/modificado(s)", tabla, filas.size()); // ← NUEVO

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
            coleccion.replaceOne(
                    Filters.eq("id", doc.get("id")),
                    doc,
                    new ReplaceOptions().upsert(true)
            );
        }
    }

    public Integer getProgress(String jobId) {
        AtomicInteger progress = jobs.get(jobId);
        return progress != null ? progress.get() : null;
    }
}