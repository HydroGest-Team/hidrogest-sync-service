package com.paqueteria.paqueteria_tracker.service;

import com.paqueteria.paqueteria_tracker.model.EventoPaquete;
import com.paqueteria.paqueteria_tracker.repository.EventoPaqueteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaqueteService {

    private final EventoPaqueteRepository repository;

    public EventoPaquete registrarPaquete(EventoPaquete paquete) {
        paquete.setAccion("INSERT");
        paquete.setEstado("REGISTRADO");
        paquete.setFechaEvento(LocalDateTime.now());
        return repository.save(paquete);
    }

    public EventoPaquete actualizarEstado(String codigoPaquete, String nuevoEstado, String descripcion) {
        EventoPaquete evento = new EventoPaquete();
        evento.setCodigoPaquete(codigoPaquete);
        evento.setEstado(nuevoEstado);
        evento.setDescripcion(descripcion);
        evento.setAccion("UPDATE");
        evento.setFechaEvento(LocalDateTime.now());
        return repository.save(evento);
    }

    public List<EventoPaquete> obtenerHistorial(String codigoPaquete) {
        return repository.findByCodigoPaquete(codigoPaquete);
    }

    public List<EventoPaquete> obtenerTodos() {
        return repository.findAll();
    }
}