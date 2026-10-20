package com.mapsug.spell.core;

import com.mapsug.spell.text.Language;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-market settings. Protected tokens are short function words and address abbreviations that
 * are valid even when rare and must never be rewritten ("col", "av", "jl", "st").
 */
public record CountryProfile(String code, String name, Language language, Set<String> protectedTokens) {

    private static final Map<String, CountryProfile> PROFILES = Map.of(
            "MX", new CountryProfile("MX", "México", Language.ES, Set.of(
                    "de", "del", "la", "las", "los", "el", "en", "y", "e", "a", "al", "san", "sta", "sto",
                    "av", "ave", "col", "blvd", "calz", "carr", "km", "no", "num", "int", "ext", "mz", "lt",
                    "cp", "edo", "mex", "cdmx", "sn")),
            "GB", new CountryProfile("GB", "United Kingdom", Language.EN, Set.of(
                    "the", "of", "and", "at", "in", "on", "a", "an", "st", "rd", "ave", "ln", "sq", "pl",
                    "dr", "ct", "uk", "nhs", "mr", "mrs")),
            "MY", new CountryProfile("MY", "Malaysia", Language.MS, Set.of(
                    "di", "ke", "dan", "jln", "jl", "lrg", "tmn", "kg", "bdr", "bt", "sg", "kl", "klcc",
                    "lrt", "mrt", "no", "sdn", "bhd")),
            "ID", new CountryProfile("ID", "Indonesia", Language.ID, Set.of(
                    "di", "ke", "dan", "jl", "jln", "gg", "no", "rt", "rw", "kel", "kec", "kab", "pt", "cv",
                    "tbk", "rs", "rsud", "sd", "smp", "sma", "dki")));

    public static CountryProfile of(String code) {
        CountryProfile p = PROFILES.get(code.toUpperCase(Locale.ROOT));
        if (p == null) {
            throw new IllegalArgumentException("Unsupported country: " + code + " (supported: " + supported() + ")");
        }
        return p;
    }

    public static boolean isSupported(String code) {
        return code != null && PROFILES.containsKey(code.toUpperCase(Locale.ROOT));
    }

    public static Set<String> supported() {
        return new java.util.TreeSet<>(PROFILES.keySet());
    }
}
