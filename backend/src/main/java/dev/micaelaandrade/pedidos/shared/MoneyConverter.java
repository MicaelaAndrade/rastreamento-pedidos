package dev.micaelaandrade.pedidos.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Guarda valores em dinheiro como centavos (número inteiro).
 * O SQLite não tem tipo decimal exato: um NUMERIC pode virar ponto flutuante
 * e 25.90 voltar como 25.899999... Com centavos inteiros não há perda.
 */
@Converter
public class MoneyConverter implements AttributeConverter<BigDecimal, Long> {

    @Override
    public Long convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact();
    }

    @Override
    public BigDecimal convertToEntityAttribute(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }
}
