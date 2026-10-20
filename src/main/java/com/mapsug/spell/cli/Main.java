package com.mapsug.spell.cli;

import com.mapsug.spell.core.CorrectionResult;
import com.mapsug.spell.core.SpellConfig;
import com.mapsug.spell.core.SpellCorrector;
import com.mapsug.spell.core.SpellService;
import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.pipeline.TrainingPipeline;
import com.mapsug.spell.sample.SampleDataGenerator;
import com.mapsug.spell.server.HttpApi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands:
 * <pre>
 *   gen-sample [--out data/sample] [--queries 6000] [--seed 42]
 *   train      [--data data/sample] [--model model]
 *   correct    [--data ..] [--model ..] --country MX "query" ["query" ...]
 *   bench      [--data ..] [--model ..] [--iterations 20000]
 *   serve      [--data ..] [--model ..] [--host 0.0.0.0] [--port 18765]
 * </pre>
 * A trailing space in a query means the last word is finished; without it the last word is
 * treated as an unfinished prefix.
 */
public final class Main {

    public static void main(String[] argv) throws Exception {
        if (argv.length == 0) {
            usage();
            return;
        }
        String cmd = argv[0];
        Map<String, String> opt = new HashMap<>();
        List<String> pos = new ArrayList<>();
        for (int i = 1; i < argv.length; i++) {
            if (argv[i].startsWith("--") && i + 1 < argv.length) {
                opt.put(argv[i].substring(2), argv[++i]);
            } else {
                pos.add(argv[i]);
            }
        }
        Path data = Path.of(opt.getOrDefault("data", "data/sample"));
        Path model = Path.of(opt.getOrDefault("model", "model"));
        SpellConfig config = new SpellConfig();
        if (opt.containsKey("trust-freq")) {
            config.trustFreq = Long.parseLong(opt.get("trust-freq"));
        }
        switch (cmd) {
            case "gen-sample" -> {
                Path out = Path.of(opt.getOrDefault("out", "data/sample"));
                Dataset d = new SampleDataGenerator(Long.parseLong(opt.getOrDefault("seed", "42")),
                        Integer.parseInt(opt.getOrDefault("queries", "6000"))).generate();
                d.write(out);
                System.out.printf("wrote %s: %d query/top1 rows, %d triples, %d token stats, %d click rows%n", out,
                        d.queryTop1().size(), d.triples().size(), d.tokenStats().size(), d.queryClicks().size());
            }
            case "train" -> new TrainingPipeline(config).run(data, model);
            case "correct" -> {
                String country = opt.getOrDefault("country", "MX");
                SpellService s = SpellService.load(data, model, config);
                boolean debug = Boolean.parseBoolean(opt.getOrDefault("debug", "false"));
                for (String q : pos) {
                    CorrectionResult r = s.correct(country, q);
                    if (debug) {
                        SpellCorrector c = s.correctors().get(country.toUpperCase(java.util.Locale.ROOT));
                        SpellCorrector.Analysis a = c.analyze(q);
                        c.decide(a, c.model());
                        for (int i = 0; i < a.tokens().size(); i++) {
                            System.out.println("  token '" + a.tokens().get(i).raw() + "': " + a.lattice().get(i));
                        }
                        System.out.printf("  margin=%.3f threshold=%.2f contextWeight=%.2f%n", r.scoreMargin(),
                                c.model().acceptThreshold, c.model().contextWeight);
                    }
                    System.out.printf("[%s] \"%s\" -> \"%s\"  changed=%s conf=%.2f %dus%n", country, q, r.corrected(),
                            r.changed(), r.confidence(), r.latencyMicros());
                    for (CorrectionResult.TokenResult t : r.tokens()) {
                        System.out.printf("    %-14s -> %-14s %s cands=%d %s%n", t.original(), t.corrected(),
                                t.complete() ? "complete" : "prefix  ", t.candidates(), t.changed() ? t.sources() : "");
                    }
                }
            }
            case "bench" -> bench(SpellService.load(data, model, config), Integer.parseInt(opt.getOrDefault("iterations", "20000")));
            case "serve" -> {
                SpellService s = SpellService.load(data, model, config);
                s.warmUp(200);
                HttpApi api = new HttpApi(s);
                String host = opt.getOrDefault("host", "0.0.0.0");
                int port = Integer.parseInt(opt.getOrDefault("port", "18765"));
                api.start(host, port);
                System.out.printf("Map Sug spelling service listening on http://%s:%d (countries %s)%n", host, port,
                        s.correctors().keySet());
            }
            default -> usage();
        }
    }

    private static void bench(SpellService s, int iterations) {
        String[] probes = {"starbuks cof", "ecatepec de moaalos", "aple", "starbucks coefe", "jalan sudirmn ",
                "kuala lumpr", "tesco exprss", "bandara soekarno hata", "picadilly circ", "coyoacan",
                "palacio de hierro pol", "petronas twin towrs", "stasiun gamb"};
        for (Map.Entry<String, SpellCorrector> e : s.correctors().entrySet()) {
            SpellCorrector c = e.getValue();
            for (int i = 0; i < 2000; i++) {
                c.decide(c.analyze(probes[i % probes.length]), c.model());
            }
            long[] lat = new long[iterations];
            for (int i = 0; i < iterations; i++) {
                long t0 = System.nanoTime();
                c.decide(c.analyze(probes[i % probes.length]), c.model());
                lat[i] = System.nanoTime() - t0;
            }
            Arrays.sort(lat);
            System.out.printf("%s uncached: p50=%.0fus p99=%.0fus p999=%.0fus max=%.0fus%n", e.getKey(),
                    lat[iterations / 2] / 1e3, lat[(int) (iterations * 0.99)] / 1e3,
                    lat[(int) (iterations * 0.999)] / 1e3, lat[iterations - 1] / 1e3);
        }
    }

    private static void usage() {
        System.out.println("""
                usage: sug-spell <command> [options]
                  gen-sample [--out data/sample] [--queries 6000] [--seed 42]
                  train      [--data data/sample] [--model model]
                  correct    [--data DIR] [--model DIR] --country MX "query" ...
                  bench      [--data DIR] [--model DIR] [--iterations 20000]
                  serve      [--data DIR] [--model DIR] [--host 0.0.0.0] [--port 18765]
                """);
    }
}
