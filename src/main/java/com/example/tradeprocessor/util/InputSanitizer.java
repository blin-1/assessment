package com.example.tradeprocessor.util;

import com.example.tradeprocessor.model.InputTrade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;

public final class InputSanitizer {

    private InputSanitizer() {}

    public static InputTrade sanitizedCopy(InputTrade in) {
        if (in == null) return null;
        var copy = new InputTrade();
        copy.setTradeId(in.getTradeId());
        copy.setAccountName(redactName(in.getAccountName()));
        copy.setAccountNumber(maskAccount(in.getAccountNumber()));
        copy.setAmount(in.getAmount());
        copy.setCurrency(in.getCurrency());
        copy.setSecurityId(in.getSecurityId());
        copy.setTradeDate(in.getTradeDate());
        copy.setTradeType(in.getTradeType());
        return copy;
    }

    public static String maskAccount(String a) {
        if (a == null) return null;
        int mask = 4;
        int len = a.length();
        if (len <= mask) return "*".repeat(len);
        var sb = new StringBuilder();
        for (int i = 0; i < mask; i++) sb.append('*');
        sb.append(a.substring(mask));
        return sb.toString();
    }

    public static String redactName(String name) {
        if (name == null) return null;
        var parts = name.trim().split("\\s+");
        var sb = new StringBuilder();
        for (var p : parts) {
            if (p.length() > 0) sb.append(p.charAt(0)).append('.');
        }
        return sb.toString();
    }

    /**
     * Return a sanitized JSON string where common sensitive fields (accountNumber, account_name)
     * are masked when present. If parsing fails, returns a short placeholder.
     */
    public static String sanitizeJsonPayload(String payload, ObjectMapper mapper) {
        if (payload == null) return "<null>";
        try {
            JsonNode node = mapper.readTree(payload);
            maskField(node, "accountNumber");
            maskField(node, "account_number");
            maskField(node, "accountName");
            maskField(node, "account_name");
            // remove any extremely large binary fields if present (defensive)
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "<unparseable-payload>";
        }
    }

    private static void maskField(JsonNode node, String field) {
        if (node == null || !node.isObject()) return;
    var obj = (ObjectNode) node;
        if (obj.has(field) && obj.get(field).isTextual()) {
            var original = obj.get(field).asText();
            obj.put(field, maskAccount(original));
        }
        // also check nested nodes shallowly
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            JsonNode child = obj.get(name);
            if (child != null && child.isObject()) {
                maskField(child, field);
            }
        }
    }
}
