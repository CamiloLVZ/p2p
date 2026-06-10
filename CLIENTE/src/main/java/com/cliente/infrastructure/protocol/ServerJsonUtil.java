package com.cliente.infrastructure.protocol;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ServerJsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ServerJsonUtil() {}

    public static String toJson(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return mapper.readValue(json, clazz);
    }

    public static <T> T convert(Object obj, Class<T> clazz) {
        return mapper.convertValue(obj, clazz);
    }

    public static <T> List<T> convertList(Object raw, Class<T> elementClass) {
        if (raw == null) return new ArrayList<>();
        return mapper.convertValue(raw,
                mapper.getTypeFactory().constructCollectionType(List.class, elementClass));
    }

    public static <T> Mensaje<T> buildRequest(Accion accion, T payload, String clientId, Protocolo protocolo) {
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());
        meta.setClientId(clientId);
        meta.setProtocolo(protocolo);

        Mensaje<T> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(accion);
        msg.setMetadata(meta);
        msg.setPayload(payload);
        return msg;
    }
}
