package dev.micaelaandrade.pedidos.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Guarda datas como texto ISO-8601 em UTC com milissegundos fixos (ex.: 2026-10-16T19:30:00.000Z).
 * O SQLite não tem tipo de data; texto num formato fixo é legível e ordena corretamente.
 */
@Converter
public class InstantConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Override
    public String convertToDatabaseColumn(Instant value) {
        return value == null ? null : FORMAT.format(value.truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public Instant convertToEntityAttribute(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
