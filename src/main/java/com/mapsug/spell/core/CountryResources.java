package com.mapsug.spell.core;

import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.data.Records;
import com.mapsug.spell.data.TripleCleaner;
import com.mapsug.spell.dict.BigramModel;
import com.mapsug.spell.dict.ErrorModel;
import com.mapsug.spell.dict.KnownCorrections;
import com.mapsug.spell.dict.SymSpellIndex;
import com.mapsug.spell.dict.TokenDictionary;
import com.mapsug.spell.text.Phonetic;
import com.mapsug.spell.text.Tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All read-only indexes of one country, built offline from the four tables:
 * <ul>
 *   <li>token stats + Top1 words -> {@link TokenDictionary} (with prefix trie) and {@link SymSpellIndex};</li>
 *   <li>Top1 names/addresses + clicked results -> {@link BigramModel} (context);</li>
 *   <li>cleaned triples -> {@link KnownCorrections} and {@link ErrorModel};</li>
 *   <li>dictionary -> phonetic buckets.</li>
 * </ul>
 */
public final class CountryResources {

    public final CountryProfile profile;
    public final SpellConfig config;
    public final TokenDictionary dict;
    public final SymSpellIndex symSpell;
    public final BigramModel bigram;
    public final KnownCorrections known;
    public final ErrorModel errorModel;
    public final Map<String, int[]> phoneticIndex;
    public final long trustFreq;
    public final TripleCleaner.Result cleanedTriples;

    private CountryResources(CountryProfile profile, SpellConfig config, TokenDictionary dict, SymSpellIndex symSpell,
                             BigramModel bigram, KnownCorrections known, ErrorModel errorModel,
                             Map<String, int[]> phoneticIndex, long trustFreq, TripleCleaner.Result cleanedTriples) {
        this.profile = profile;
        this.config = config;
        this.dict = dict;
        this.symSpell = symSpell;
        this.bigram = bigram;
        this.known = known;
        this.errorModel = errorModel;
        this.phoneticIndex = phoneticIndex;
        this.trustFreq = trustFreq;
        this.cleanedTriples = cleanedTriples;
    }

    /**
     * @param data     rows of this country only
     * @param triples  triples used for the error model / known corrections (the training split)
     */
    public static CountryResources build(CountryProfile profile, Dataset data, List<Records.Triple> triples, SpellConfig config) {
        TokenDictionary.Builder db = TokenDictionary.builder();
        for (Records.TokenStat s : data.tokenStats()) {
            for (String w : Tokenizer.words(s.token())) {
                db.add(w, s.df());
            }
        }
        BigramModel bigram = new BigramModel();
        for (Records.QueryTop1 r : data.queryTop1()) {
            addPlaceText(db, bigram, r.top1Name(), 1);
            addPlaceText(db, bigram, r.top1Address(), 1);
        }
        for (Records.QueryClick c : data.queryClicks()) {
            addPlaceText(db, bigram, c.clickedName(), Math.max(1, c.clicks()));
        }
        TokenDictionary dict = db.build(config.dictMinFreq);
        bigram.freeze();

        long trust = config.trustFreq > 0 ? config.trustFreq : deriveTrustFreq(dict);
        TripleCleaner.Result cleaned = TripleCleaner.clean(triples, dict, trust, config.noiseWrongRightRatio);
        KnownCorrections known = KnownCorrections.build(cleaned.pairCounts(), config.knownMinCount, config.knownMinShare);
        ErrorModel em = new ErrorModel(config.errorModelAlpha);
        for (Records.Triple t : cleaned.kept()) {
            em.observe(t.wrong(), t.right(), t.count());
        }
        SymSpellIndex sym = new SymSpellIndex(dict, config.symSpellMaxDistance, config.symSpellPrefixLength, config.dictMinFreq);
        Map<String, int[]> phon = buildPhonetic(dict, profile, config.phoneticBucketSize);
        return new CountryResources(profile, config, dict, sym, bigram, known, em, phon, trust, cleaned);
    }

    private static void addPlaceText(TokenDictionary.Builder db, BigramModel bigram, String text, long weight) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> words = Tokenizer.words(text);
        for (com.mapsug.spell.text.Token t : Tokenizer.tokenize(text)) {
            db.add(t.raw(), 1);
        }
        bigram.addSentence(words, weight);
    }

    /**
     * Frequency above which a token is considered certainly valid. Typos are long-tail, so the
     * 75th percentile of the vocabulary (floored at 20) separates them well on real query logs.
     */
    static long deriveTrustFreq(TokenDictionary dict) {
        if (dict.size() == 0) {
            return 20;
        }
        long[] f = new long[dict.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = dict.freq(i);
        }
        Arrays.sort(f);
        return Math.max(20, f[(int) (f.length * 0.75)]);
    }

    private static Map<String, int[]> buildPhonetic(TokenDictionary dict, CountryProfile profile, int bucket) {
        Map<String, List<Integer>> tmp = new HashMap<>();
        for (int id = 0; id < dict.size(); id++) {
            String k = dict.key(id);
            if (k.length() < 3) {
                continue;
            }
            tmp.computeIfAbsent(Phonetic.key(k, profile.language()), x -> new ArrayList<>()).add(id);
        }
        Map<String, int[]> out = new HashMap<>(tmp.size() * 2);
        for (Map.Entry<String, List<Integer>> e : tmp.entrySet()) {
            List<Integer> ids = e.getValue();
            ids.sort((a, b) -> Long.compare(dict.freq(b), dict.freq(a)));
            int n = Math.min(bucket, ids.size());
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = ids.get(i);
            }
            out.put(e.getKey(), arr);
        }
        return out;
    }

    public String summary() {
        return String.format("%s vocab=%d symspellKeys=%d bigrams=%d knownTypos=%d errorOps=%d trustFreq=%d triplesKept=%d dropped=%s",
                profile.code(), dict.size(), symSpell.entries(), bigram.bigramCount(), known.size(),
                errorModel.distinctOps(), trustFreq, cleanedTriples.kept().size(), cleanedTriples.dropReasons());
    }
}
