package com.example.velocitysuites.network;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * The backend serializes nullable DB columns as explicit JSON nulls rather than omitting the
 * key (e.g. a direct Booking's not-yet-assigned room, a not-yet-linked billing_id, a room_type's
 * availability accessor computed without a date range). Several response DTOs declare those
 * fields as Java primitives (long/int/boolean/etc.), and Gson's default adapters throw
 * "java.lang.IllegalStateException: Expected a long but was NULL" the moment one of those
 * fields meets a JSON null - Retrofit routes that exception straight to Call#onFailure(), which
 * is why it was surfacing as an opaque "Network error: java.lang.IllegalStateException" toast
 * instead of a normal successful response. This factory makes every primitive-typed field
 * null-tolerant by substituting the type's zero value, matching how the same field would behave
 * if the key were simply absent.
 */
final class NullSafePrimitiveTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();
        if (!rawType.isPrimitive()) {
            return null;
        }
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        Object zeroValue = zeroValueFor(rawType);
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return (T) zeroValue;
                }
                return delegate.read(in);
            }
        };
    }

    private static Object zeroValueFor(Class<?> primitiveType) {
        if (primitiveType == boolean.class) return Boolean.FALSE;
        if (primitiveType == int.class) return 0;
        if (primitiveType == long.class) return 0L;
        if (primitiveType == double.class) return 0d;
        if (primitiveType == float.class) return 0f;
        if (primitiveType == short.class) return (short) 0;
        if (primitiveType == byte.class) return (byte) 0;
        if (primitiveType == char.class) return (char) 0;
        return null;
    }
}
