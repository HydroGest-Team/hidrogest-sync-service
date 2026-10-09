package com.paqueteria.paqueteria_tracker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "eventos_paquete")
public class EventoPaquete {

    @Id
    private String id;

    private String codigoPaquete;
    private String remitente;
    private String destinatario;
    private String origen;
    private String destino;
    private String estado;       // REGISTRADO, EN_TRANSITO, ENTREGADO, DEVUELTO
    private String descripcion;
    private String accion;       // INSERT, UPDATE
    private LocalDateTime fechaEvento;
}