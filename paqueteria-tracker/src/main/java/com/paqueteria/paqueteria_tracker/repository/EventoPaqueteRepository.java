package com.paqueteria.paqueteria_tracker.repository;

import com.paqueteria.paqueteria_tracker.model.EventoPaquete;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoPaqueteRepository extends MongoRepository<EventoPaquete, String> {
    List<EventoPaquete> findByCodigoPaquete(String codigoPaquete);
}