package com.mapsug.spell.server;

import com.mapsug.spell.core.CorrectionResult;

/** Minimal JSON encoding for the API responses (keeps the service dependency-free). */
final class Json {

    private Json() {
    }

    static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static String num(double d) {
        return Double.isFinite(d) ? String.format(java.util.Locale.ROOT, "%.4f", d) : "null";
    }

    static String result(CorrectionResult r) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"country\":").append(str(r.country())).append(',')
                .append("\"original\":").append(str(r.original())).append(',')
                .append("\"corrected\":").append(str(r.corrected())).append(',')
                .append("\"changed\":").append(r.changed()).append(',')
                .append("\"confidence\":").append(num(r.confidence())).append(',')
                .append("\"scoreMargin\":").append(num(r.scoreMargin())).append(',')
                .append("\"latencyMicros\":").append(r.latencyMicros()).append(',')
                .append("\"cached\":").append(r.cached()).append(',')
                .append("\"degraded\":").append(r.degraded()).append(',')
                .append("\"tokens\":[");
        for (int i = 0; i < r.tokens().size(); i++) {
            CorrectionResult.TokenResult t = r.tokens().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"original\":").append(str(t.original())).append(',')
                    .append("\"corrected\":").append(str(t.corrected())).append(',')
                    .append("\"complete\":").append(t.complete()).append(',')
                    .append("\"changed\":").append(t.changed()).append(',')
                    .append("\"sources\":").append(str(t.sources())).append(',')
                    .append("\"candidates\":").append(t.candidates()).append(',')
                    .append("\"score\":").append(num(t.score()))
                    .append('}');
        }
        return sb.append("]}").toString();
    }
}
