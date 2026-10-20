package com.mapsug.spell.core;

import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.rank.RankerModel;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Multi-country facade: one {@link SpellCorrector} per market, routed by country code. */
public final class SpellService {

    private final Map<String, SpellCorrector> correctors;

    public SpellService(Map<String, SpellCorrector> correctors) {
        this.correctors = correctors;
    }

    /** Builds every country present in {@code dataDir}; loads {@code modelDir/<CC>.properties} when available. */
    public static SpellService load(Path dataDir, Path modelDir, SpellConfig config) {
        Dataset all = Dataset.load(dataDir);
        Map<String, SpellCorrector> map = new LinkedHashMap<>();
        for (String cc : all.countries()) {
            if (!CountryProfile.isSupported(cc)) {
                System.err.println("[sug-spell] skipping unsupported country " + cc);
                continue;
            }
            Dataset d = all.forCountry(cc);
            CountryResources res = CountryResources.build(CountryProfile.of(cc), d, d.triples(), config);
            RankerModel model = RankerModel.loadOrDefault(modelDir == null ? null : modelDir.resolve(cc + ".properties"));
            map.put(cc, new SpellCorrector(res, model));
            System.err.println("[sug-spell] loaded " + res.summary());
        }
        return new SpellService(map);
    }

    public CorrectionResult correct(String country, String query) {
        SpellCorrector c = correctors.get(country.toUpperCase(Locale.ROOT));
        if (c == null) {
            throw new IllegalArgumentException("Country not loaded: " + country + " (loaded: " + correctors.keySet() + ")");
        }
        return c.correct(query);
    }

    public Map<String, SpellCorrector> correctors() {
        return correctors;
    }

    /** Runs a few queries per country so the JIT has compiled the hot path before traffic arrives. */
    public void warmUp(int rounds) {
        String[] probes = {"sta", "starbuks cof", "ecatepec de moaalos", "aple", "jalan sudirmn", "tesco exp", "kuala lumpr"};
        for (SpellCorrector c : correctors.values()) {
            for (int r = 0; r < rounds; r++) {
                for (String p : probes) {
                    c.decide(c.analyze(p + (r % 2 == 0 ? "" : " ")), c.model());
                }
            }
        }
    }
}
