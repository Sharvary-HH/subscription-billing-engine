package com.sharvary.billing.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sharvary.billing.common.Money;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Money goes over the wire as {@code {"amount": "19.99", "minor": 1999, "currency": "USD"}}.
 * The decimal is a string on purpose: a JSON number would be parsed as a double on the other end.
 * Reading back (used when replaying stored idempotent responses) trusts only the integer field.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule moneyModule() {
        SimpleModule module = new SimpleModule("money");
        module.addSerializer(Money.class, new JsonSerializer<>() {
            @Override
            public void serialize(Money value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeStartObject();
                gen.writeStringField("amount", value.toMajor().toPlainString());
                gen.writeNumberField("minor", value.minor());
                gen.writeStringField("currency", value.currencyCode());
                gen.writeEndObject();
            }
        });
        module.addDeserializer(Money.class, new JsonDeserializer<>() {
            @Override
            public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                return Money.of(node.get("minor").asLong(), node.get("currency").asText());
            }
        });
        return module;
    }
}
