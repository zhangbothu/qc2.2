package com.mapsug.spell.server;

import com.mapsug.spell.core.CorrectionResult;
import com.mapsug.spell.core.CountryProfile;
import com.mapsug.spell.core.SpellCorrector;
import com.mapsug.spell.core.SpellService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP endpoints:
 * <pre>
 * GET /api/correct?country=MX&amp;q=ecatepec%20de%20moaalos   correction result (JSON)
 * GET /api/countries                                         loaded markets
 * GET /health                                                liveness
 * GET /                                                      interactive demo page
 * </pre>
 */
public final class HttpApi {

    private final SpellService service;
    private HttpServer server;

    public HttpApi(SpellService service) {
        this.service = service;
    }

    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 256);
        server.createContext("/api/correct", this::correct);
        server.createContext("/api/countries", this::countries);
        server.createContext("/health", ex -> send(ex, 200, "application/json", "{\"status\":\"ok\"}"));
        server.createContext("/", this::page);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void correct(HttpExchange ex) throws IOException {
        Map<String, String> p = query(ex.getRequestURI().getRawQuery());
        String country = p.getOrDefault("country", "");
        String q = p.get("q");
        if (q == null) {
            send(ex, 400, "application/json", "{\"error\":\"missing parameter q\"}");
            return;
        }
        if (!service.correctors().containsKey(country.toUpperCase(java.util.Locale.ROOT))) {
            send(ex, 400, "application/json", "{\"error\":" + Json.str("unknown country '" + country
                    + "', loaded: " + service.correctors().keySet()) + "}");
            return;
        }
        if (q.length() > 200) {
            q = q.substring(0, 200);
        }
        CorrectionResult r = service.correct(country, q);
        send(ex, 200, "application/json", Json.result(r));
    }

    private void countries(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, SpellCorrector> e : service.correctors().entrySet()) {
            CountryProfile pr = e.getValue().resources().profile;
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"code\":").append(Json.str(pr.code()))
                    .append(",\"name\":").append(Json.str(pr.name()))
                    .append(",\"language\":").append(Json.str(pr.language().name()))
                    .append(",\"vocabulary\":").append(e.getValue().resources().dict.size())
                    .append('}');
        }
        send(ex, 200, "application/json", sb.append(']').toString());
    }

    private void page(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            send(ex, 404, "text/plain", "not found");
            return;
        }
        try (InputStream in = HttpApi.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                send(ex, 500, "text/plain", "demo page missing from classpath");
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type.contains("charset") ? type : type + "; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
