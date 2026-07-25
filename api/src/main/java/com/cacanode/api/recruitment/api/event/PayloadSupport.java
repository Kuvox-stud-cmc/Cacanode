package com.cacanode.api.recruitment.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

final class PayloadSupport {
    private PayloadSupport() {}

    static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value != null) result.put((String) values[index], value);
        }
        return Map.copyOf(result);
    }
}
