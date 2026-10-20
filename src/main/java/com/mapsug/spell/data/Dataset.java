package com.mapsug.spell.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The four tables, loaded from tab-separated files with a header row. Columns are matched by
 * name, so extra columns are ignored and column order does not matter.
 *
 * <pre>
 * query_top1.tsv           country  query  top1_name  [top1_address]
 * correction_triples.tsv   country  wrong_token  right_token  edit_distance  [count]
 * token_stats.tsv          country  token  df
 * query_click.tsv          country  query  clicked_name  [clicks]
 * </pre>
 */
public record Dataset(List<Records.QueryTop1> queryTop1,
                      List<Records.Triple> triples,
                      List<Records.TokenStat> tokenStats,
                      List<Records.QueryClick> queryClicks) {

    public static final String QUERY_TOP1 = "query_top1.tsv";
    public static final String TRIPLES = "correction_triples.tsv";
    public static final String TOKEN_STATS = "token_stats.tsv";
    public static final String QUERY_CLICK = "query_click.tsv";

    public static Dataset load(Path dir) {
        return new Dataset(
                read(dir.resolve(QUERY_TOP1), r -> new Records.QueryTop1(
                        r.req("country"), r.untrimmed("query"), r.req("top1_name"), r.opt("top1_address", ""))),
                read(dir.resolve(TRIPLES), r -> new Records.Triple(
                        r.req("country"), r.req("wrong_token"), r.req("right_token"),
                        Integer.parseInt(r.opt("edit_distance", "-1")), Long.parseLong(r.opt("count", "1")))),
                read(dir.resolve(TOKEN_STATS), r -> new Records.TokenStat(
                        r.req("country"), r.req("token"), Long.parseLong(r.req("df")))),
                read(dir.resolve(QUERY_CLICK), r -> new Records.QueryClick(
                        r.req("country"), r.untrimmed("query"), r.req("clicked_name"), Long.parseLong(r.opt("clicks", "1")))));
    }

    public Dataset forCountry(String country) {
        String c = country.toUpperCase(Locale.ROOT);
        return new Dataset(
                filter(queryTop1, r -> r.country().equalsIgnoreCase(c)),
                filter(triples, r -> r.country().equalsIgnoreCase(c)),
                filter(tokenStats, r -> r.country().equalsIgnoreCase(c)),
                filter(queryClicks, r -> r.country().equalsIgnoreCase(c)));
    }

    public List<String> countries() {
        java.util.TreeSet<String> s = new java.util.TreeSet<>();
        tokenStats.forEach(r -> s.add(r.country().toUpperCase(Locale.ROOT)));
        queryTop1.forEach(r -> s.add(r.country().toUpperCase(Locale.ROOT)));
        return new ArrayList<>(s);
    }

    private static <T> List<T> filter(List<T> l, Predicate<T> p) {
        List<T> out = new ArrayList<>();
        for (T t : l) {
            if (p.test(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static final class Row {
        private final Map<String, Integer> header;
        private final String[] cells;
        private final Path file;
        private final int line;

        Row(Map<String, Integer> header, String[] cells, Path file, int line) {
            this.header = header;
            this.cells = cells;
            this.file = file;
            this.line = line;
        }

        String req(String col) {
            return untrimmed(col).trim();
        }

        /** Sug queries keep their trailing space: it tells whether the last word is finished. */
        String untrimmed(String col) {
            Integer i = header.get(col);
            if (i == null) {
                throw new IllegalArgumentException(file + ": missing column '" + col + "'");
            }
            if (i >= cells.length) {
                throw new IllegalArgumentException(file + ":" + line + ": missing value for '" + col + "'");
            }
            String v = cells[i];
            return v.endsWith("\r") ? v.substring(0, v.length() - 1) : v;
        }

        String opt(String col, String def) {
            Integer i = header.get(col);
            if (i == null || i >= cells.length || cells[i].isBlank()) {
                return def;
            }
            return cells[i].trim();
        }
    }

    private static <T> List<T> read(Path file, Function<Row, T> mapper) {
        List<T> out = new ArrayList<>();
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Missing data file: " + file);
        }
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String head = br.readLine();
            if (head == null) {
                return out;
            }
            Map<String, Integer> header = new HashMap<>();
            String[] h = head.replace("\uFEFF", "").split("\t", -1);
            for (int i = 0; i < h.length; i++) {
                header.put(h[i].trim().toLowerCase(Locale.ROOT), i);
            }
            String line;
            int ln = 1;
            while ((line = br.readLine()) != null) {
                ln++;
                if (line.isBlank()) {
                    continue;
                }
                out.add(mapper.apply(new Row(header, line.split("\t", -1), file, ln)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public void write(Path dir) throws IOException {
        Files.createDirectories(dir);
        try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(QUERY_TOP1), StandardCharsets.UTF_8)) {
            w.write("country\tquery\ttop1_name\ttop1_address\n");
            for (Records.QueryTop1 r : queryTop1) {
                w.write(r.country() + "\t" + r.query() + "\t" + r.top1Name() + "\t" + r.top1Address() + "\n");
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(TRIPLES), StandardCharsets.UTF_8)) {
            w.write("country\twrong_token\tright_token\tedit_distance\tcount\n");
            for (Records.Triple r : triples) {
                w.write(r.country() + "\t" + r.wrong() + "\t" + r.right() + "\t" + r.editDistance() + "\t" + r.count() + "\n");
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(TOKEN_STATS), StandardCharsets.UTF_8)) {
            w.write("country\ttoken\tdf\n");
            for (Records.TokenStat r : tokenStats) {
                w.write(r.country() + "\t" + r.token() + "\t" + r.df() + "\n");
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(QUERY_CLICK), StandardCharsets.UTF_8)) {
            w.write("country\tquery\tclicked_name\tclicks\n");
            for (Records.QueryClick r : queryClicks) {
                w.write(r.country() + "\t" + r.query() + "\t" + r.clickedName() + "\t" + r.clicks() + "\n");
            }
        }
    }
}
