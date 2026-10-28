package com.example.velocitysuites.network.dto;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Reads a money field that a receipt stores either as a plain number or - as the 1.68.0 check-in receipt did for its promo and ID
 * discount - as an object carrying the amount ({"name": ..., "amount": 700}). A receipt that is already issued is never rewritten,
 * so the app accepts both instead of failing to open the whole receipt over one figure. Anything else reads as "no amount".
 */
public final class LenientAmount extends TypeAdapter<Double> {

    @Override
    public Double read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        switch (token) {
            case NUMBER:
                return in.nextDouble();
            case STRING:
                try {
                    return Double.parseDouble(in.nextString().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            case BEGIN_OBJECT: {
                Double amount = null;
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    if ("amount".equals(name)) {
                        amount = read(in);
                    } else {
                        in.skipValue();
                    }
                }
                in.endObject();
                return amount;
            }
            case NULL:
                in.nextNull();
                return null;
            default:
                in.skipValue();
                return null;
        }
    }

    @Override
    public void write(JsonWriter out, Double value) throws IOException {
        if (value == null) out.nullValue(); else out.value(value);
    }
}
