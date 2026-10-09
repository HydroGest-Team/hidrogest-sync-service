package com.paqueteria.paqueteria_tracker.controller;

import com.paqueteria.paqueteria_tracker.model.EventoPaquete;
import com.paqueteria.paqueteria_tracker.service.PaqueteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paquetes")
@RequiredArgsConstructor
public class PaqueteController {

    private final PaqueteService service;

    @PostMapping
    public ResponseEntity<EventoPaquete> registrar(@RequestBody EventoPaquete paquete) {
        return ResponseEntity.ok(service.registrarPaquete(paquete));
    }

    @PutMapping("/{codigo}/estado")
    public ResponseEntity<EventoPaquete> actualizarEstado(
            @PathVariable String codigo,
            @RequestParam String estado,
            @RequestParam(required = false, defaultValue = "") String descripcion) {
        return ResponseEntity.ok(service.actualizarEstado(codigo, estado, descripcion));
    }

    @GetMapping("/{codigo}/historial")
    public ResponseEntity<List<EventoPaquete>> historial(@PathVariable String codigo) {
        return ResponseEntity.ok(service.obtenerHistorial(codigo));
    }

    @GetMapping
    public ResponseEntity<List<EventoPaquete>> todos() {
        return ResponseEntity.ok(service.obtenerTodos());
    }
}