package com.mapsug.spell.sample;

import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.data.Records;
import com.mapsug.spell.data.TripleExtractor;
import com.mapsug.spell.text.Keyboard;
import com.mapsug.spell.text.Token;
import com.mapsug.spell.text.Tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Synthesises the four tables for MX/GB/MY/ID in the production format so the pipeline can be
 * run end to end without real logs. It simulates Sug typing behaviour: Zipf-distributed place
 * popularity, queries cut mid-word, keyboard/phonetic/omission/transposition typos, and a small
 * share of Top1 results that point at an unrelated place (the source of noisy triples).
 */
public final class SampleDataGenerator {

    private record Place(String name, String address) {
    }

    private static final Map<String, List<Place>> PLACES = new LinkedHashMap<>();

    static {
        PLACES.put("MX", places(
                "Starbucks Coffee|Paseo de la Reforma, Ciudad de México",
                "Oxxo|Avenida Insurgentes Sur, Ciudad de México",
                "Walmart Supercenter|Ecatepec de Morelos, Estado de México",
                "Soriana Hiper|Monterrey, Nuevo León",
                "Farmacias Guadalajara|Guadalajara, Jalisco",
                "Farmacia del Ahorro|Coyoacán, Ciudad de México",
                "Liverpool Insurgentes|Avenida Insurgentes Sur, Ciudad de México",
                "Palacio de Hierro Polanco|Polanco, Ciudad de México",
                "Sanborns Madero|Centro Histórico, Ciudad de México",
                "Ecatepec de Morelos|Estado de México",
                "Ciudad Nezahualcóyotl|Estado de México",
                "Tlalnepantla de Baz|Estado de México",
                "Naucalpan de Juárez|Estado de México",
                "Coyoacán|Ciudad de México",
                "Xochimilco|Ciudad de México",
                "Colonia Roma Norte|Cuauhtémoc, Ciudad de México",
                "Colonia Condesa|Cuauhtémoc, Ciudad de México",
                "Paseo de la Reforma|Ciudad de México",
                "Ángel de la Independencia|Paseo de la Reforma, Ciudad de México",
                "Zócalo|Centro Histórico, Ciudad de México",
                "Museo Nacional de Antropología|Bosque de Chapultepec, Ciudad de México",
                "Bosque de Chapultepec|Miguel Hidalgo, Ciudad de México",
                "Aeropuerto Internacional Benito Juárez|Venustiano Carranza, Ciudad de México",
                "Terminal de Autobuses del Norte|Gustavo A. Madero, Ciudad de México",
                "Hospital Ángeles Pedregal|Tlalpan, Ciudad de México",
                "Universidad Nacional Autónoma de México|Coyoacán, Ciudad de México",
                "Estadio Azteca|Tlalpan, Ciudad de México",
                "Plaza Satélite|Naucalpan de Juárez, Estado de México",
                "Galerías Monterrey|Monterrey, Nuevo León",
                "Cancún Zona Hotelera|Quintana Roo",
                "Tacos El Califa|Colonia Condesa, Ciudad de México",
                "Sushi Itto|Polanco, Ciudad de México",
                "Home Depot Ecatepec|Ecatepec de Morelos, Estado de México",
                "Costco Satélite|Naucalpan de Juárez, Estado de México",
                "Banco Azteca|Ecatepec de Morelos, Estado de México",
                "BBVA Bancomer|Paseo de la Reforma, Ciudad de México",
                "Cinépolis Plaza Universidad|Benito Juárez, Ciudad de México",
                "Metro Pantitlán|Iztacalco, Ciudad de México",
                "Mercado de la Merced|Venustiano Carranza, Ciudad de México",
                "Basílica de Guadalupe|Gustavo A. Madero, Ciudad de México",
                "Apple Store Antara|Polanco, Ciudad de México",
                "Puerto Vallarta|Jalisco",
                "Hospital General de México|Cuauhtémoc, Ciudad de México"));
        PLACES.put("GB", places(
                "Starbucks Coffee|Oxford Street, London",
                "Costa Coffee|Victoria Street, London",
                "Pret A Manger|Liverpool Street, London",
                "Tesco Express|Camden High Street, London",
                "Tesco Extra|Manchester",
                "Sainsbury's Local|Kensington, London",
                "Marks and Spencer|Oxford Street, London",
                "Waitrose|Canary Wharf, London",
                "Boots Pharmacy|Piccadilly, London",
                "Apple Store Regent Street|Regent Street, London",
                "King's Cross Station|Euston Road, London",
                "St Pancras International|Euston Road, London",
                "Paddington Station|Praed Street, London",
                "Victoria Station|Victoria, London",
                "Piccadilly Circus|Westminster, London",
                "Leicester Square|Westminster, London",
                "Covent Garden|Westminster, London",
                "Buckingham Palace|Westminster, London",
                "British Museum|Great Russell Street, London",
                "Natural History Museum|Cromwell Road, London",
                "Tower of London|Tower Hill, London",
                "London Bridge|Southwark, London",
                "Westminster Abbey|Westminster, London",
                "Heathrow Airport Terminal|Hounslow, London",
                "Gatwick Airport|Crawley, West Sussex",
                "Manchester Piccadilly|Manchester",
                "Birmingham New Street|Birmingham",
                "Edinburgh Castle|Castlehill, Edinburgh",
                "Royal Mile|Edinburgh",
                "Liverpool Lime Street|Liverpool",
                "Bristol Temple Meads|Bristol",
                "Cambridge University|Cambridge",
                "Wembley Stadium|Wembley, London",
                "Old Trafford|Manchester",
                "Emirates Stadium|Holloway, London",
                "Nando's Restaurant|Leicester Square, London",
                "Greggs Bakery|Newcastle upon Tyne",
                "Premier Inn|Southwark, London",
                "Westfield Stratford|Stratford, London",
                "Camden Market|Camden, London",
                "Borough Market|Southwark, London",
                "Hyde Park|Westminster, London",
                "Canary Wharf|Tower Hamlets, London",
                "Royal London Hospital|Whitechapel, London"));
        PLACES.put("MY", places(
                "Kuala Lumpur City Centre|Kuala Lumpur",
                "Petronas Twin Towers|Kuala Lumpur City Centre, Kuala Lumpur",
                "Pavilion Kuala Lumpur|Jalan Bukit Bintang, Kuala Lumpur",
                "Jalan Bukit Bintang|Kuala Lumpur",
                "Jalan Tuanku Abdul Rahman|Kuala Lumpur",
                "Masjid Negara|Jalan Perdana, Kuala Lumpur",
                "Batu Caves|Gombak, Selangor",
                "Mid Valley Megamall|Lingkaran Syed Putra, Kuala Lumpur",
                "Sunway Pyramid|Bandar Sunway, Selangor",
                "Kuala Lumpur International Airport|Sepang, Selangor",
                "Stesen Sentral Kuala Lumpur|Brickfields, Kuala Lumpur",
                "Stesen Pasar Seni|Kuala Lumpur",
                "Hospital Kuala Lumpur|Jalan Pahang, Kuala Lumpur",
                "Universiti Malaya|Jalan Universiti, Kuala Lumpur",
                "Petaling Jaya|Selangor",
                "Shah Alam|Selangor",
                "Subang Jaya|Selangor",
                "Putrajaya|Wilayah Persekutuan",
                "Cyberjaya|Selangor",
                "Johor Bahru|Johor",
                "Georgetown|Pulau Pinang",
                "Melaka|Melaka",
                "Ipoh|Perak",
                "Kota Kinabalu|Sabah",
                "Kuching|Sarawak",
                "Mydin Mall|Subang Jaya, Selangor",
                "Aeon Mall|Shah Alam, Selangor",
                "Starbucks Coffee|Jalan Bukit Bintang, Kuala Lumpur",
                "Nasi Kandar Pelita|Jalan Ampang, Kuala Lumpur",
                "Restoran Nasi Lemak Tanglin|Jalan Cenderasari, Kuala Lumpur",
                "Pasar Malam Taman Connaught|Cheras, Kuala Lumpur",
                "Taman Tasik Perdana|Kuala Lumpur",
                "Menara Kuala Lumpur|Jalan Puncak, Kuala Lumpur",
                "Balai Polis Dang Wangi|Kuala Lumpur",
                "Klinik Kesihatan Petaling Jaya|Selangor",
                "Pejabat Pos Besar|Kuala Lumpur",
                "Lebuhraya Persekutuan|Selangor",
                "Pantai Cenang|Langkawi, Kedah"));
        PLACES.put("ID", places(
                "Jakarta Selatan|DKI Jakarta",
                "Jakarta Pusat|DKI Jakarta",
                "Jalan Sudirman|Jakarta Pusat, DKI Jakarta",
                "Jalan Thamrin|Jakarta Pusat, DKI Jakarta",
                "Monumen Nasional|Gambir, Jakarta Pusat",
                "Bandara Soekarno Hatta|Tangerang, Banten",
                "Stasiun Gambir|Jakarta Pusat",
                "Stasiun Manggarai|Jakarta Selatan",
                "Grand Indonesia|Jalan Thamrin, Jakarta Pusat",
                "Plaza Indonesia|Jalan Thamrin, Jakarta Pusat",
                "Mall Kelapa Gading|Jakarta Utara",
                "Pondok Indah Mall|Jakarta Selatan",
                "Indomaret|Jalan Kemang Raya, Jakarta Selatan",
                "Alfamart|Jalan Margonda Raya, Depok",
                "Starbucks Coffee|Jalan Sudirman, Jakarta Pusat",
                "Kopi Kenangan|Jalan Kemang Raya, Jakarta Selatan",
                "Rumah Sakit Cipto Mangunkusumo|Jalan Diponegoro, Jakarta Pusat",
                "Universitas Indonesia|Depok, Jawa Barat",
                "Institut Teknologi Bandung|Jalan Ganesha, Bandung",
                "Malioboro|Yogyakarta",
                "Candi Borobudur|Magelang, Jawa Tengah",
                "Candi Prambanan|Sleman, Yogyakarta",
                "Pantai Kuta|Badung, Bali",
                "Ubud|Gianyar, Bali",
                "Surabaya|Jawa Timur",
                "Semarang|Jawa Tengah",
                "Bekasi|Jawa Barat",
                "Tangerang|Banten",
                "Kebun Raya Bogor|Bogor, Jawa Barat",
                "Masjid Istiqlal|Jakarta Pusat",
                "Gereja Katedral Jakarta|Jakarta Pusat",
                "Pasar Tanah Abang|Jakarta Pusat",
                "Taman Mini Indonesia Indah|Jakarta Timur",
                "Ancol Dreamland|Jakarta Utara",
                "Kantor Pos Pasar Baru|Jakarta Pusat",
                "Apotek Kimia Farma|Jalan Margonda Raya, Depok",
                "Bank Mandiri|Jalan Sudirman, Jakarta Pusat",
                "Bank Central Asia|Jalan Thamrin, Jakarta Pusat",
                "Sate Khas Senayan|Jalan Kebon Sirih, Jakarta Pusat",
                "Terminal Kampung Rambutan|Jakarta Timur"));
    }

    private static List<Place> places(String... rows) {
        List<Place> out = new ArrayList<>();
        for (String r : rows) {
            String[] p = r.split("\\|", -1);
            out.add(new Place(p[0], p[1]));
        }
        return out;
    }

    private final Random rnd;
    private final int queriesPerCountry;

    public SampleDataGenerator(long seed, int queriesPerCountry) {
        this.rnd = new Random(seed);
        this.queriesPerCountry = queriesPerCountry;
    }

    public Dataset generate() {
        List<Records.QueryTop1> top1 = new ArrayList<>();
        List<Records.Triple> triples = new ArrayList<>();
        List<Records.TokenStat> stats = new ArrayList<>();
        List<Records.QueryClick> clicks = new ArrayList<>();
        for (Map.Entry<String, List<Place>> e : PLACES.entrySet()) {
            String cc = e.getKey();
            List<Place> places = new ArrayList<>(e.getValue());
            java.util.Collections.shuffle(places, rnd);
            double[] cdf = zipf(places.size(), 0.9);
            Map<String, long[]> df = new HashMap<>();
            Map<String, Place> queryTop1 = new LinkedHashMap<>();
            Map<String, long[]> queryCount = new LinkedHashMap<>();
            for (int i = 0; i < queriesPerCountry; i++) {
                Place p = places.get(sample(cdf));
                String q = makeQuery(cc, p);
                if (q.isBlank()) {
                    continue;
                }
                Place shown = rnd.nextDouble() < 0.03 ? places.get(rnd.nextInt(places.size())) : p;
                queryTop1.putIfAbsent(q, shown);
                queryCount.computeIfAbsent(q, k -> new long[1])[0]++;
                for (Token t : Tokenizer.tokenize(q)) {
                    if (t.complete()) {
                        df.computeIfAbsent(t.raw().toLowerCase(Locale.ROOT), k -> new long[1])[0]++;
                    }
                }
            }
            for (Map.Entry<String, Place> qe : queryTop1.entrySet()) {
                Place p = qe.getValue();
                top1.add(new Records.QueryTop1(cc, qe.getKey(), p.name, p.address));
                clicks.add(new Records.QueryClick(cc, qe.getKey(), p.name, queryCount.get(qe.getKey())[0]));
            }
            Map<String, long[]> tripleCounts = new LinkedHashMap<>();
            for (Map.Entry<String, Place> qe : queryTop1.entrySet()) {
                long n = queryCount.get(qe.getKey())[0];
                for (TripleExtractor.Fix f : TripleExtractor.extract(qe.getKey(), qe.getValue().name, qe.getValue().address)) {
                    tripleCounts.computeIfAbsent(f.wrong() + "\t" + f.right() + "\t" + f.distance(), k -> new long[1])[0] += n;
                }
            }
            for (Map.Entry<String, long[]> te : tripleCounts.entrySet()) {
                String[] p = te.getKey().split("\t");
                triples.add(new Records.Triple(cc, p[0], p[1], Integer.parseInt(p[2]), te.getValue()[0]));
            }
            for (Map.Entry<String, long[]> de : df.entrySet()) {
                stats.add(new Records.TokenStat(cc, de.getKey(), de.getValue()[0]));
            }
        }
        return new Dataset(top1, triples, stats, clicks);
    }

    private String makeQuery(String cc, Place p) {
        String base = p.name;
        double r = rnd.nextDouble();
        if (r < 0.12) {
            String[] addr = p.address.split(",");
            base = p.name + " " + addr[0].trim();
        }
        List<String> words = new ArrayList<>(List.of(base.split(" ")));
        for (int i = 0; i < words.size(); i++) {
            double t = rnd.nextDouble();
            if (words.get(i).length() >= 4 && t < 0.13) {
                String w = typo(cc, words.get(i));
                if (w.length() >= 7 && rnd.nextDouble() < 0.3) {
                    w = typo(cc, w);
                }
                words.set(i, w);
            }
        }
        String full = String.join(" ", words);
        if (rnd.nextDouble() < 0.7) {
            full = full.toLowerCase(Locale.ROOT);
        }
        double cut = rnd.nextDouble();
        if (cut < 0.55 && full.length() > 3) {
            int at = 3 + rnd.nextInt(full.length() - 2);
            full = full.substring(0, Math.min(at, full.length()));
        } else if (cut < 0.65) {
            full = full + " ";
        }
        return full.replace("\t", " ");
    }

    private String typo(String cc, String w) {
        double r = rnd.nextDouble();
        int i = 1 + rnd.nextInt(Math.max(1, w.length() - 1));
        char c = Character.toLowerCase(w.charAt(i));
        if (r < 0.30) {
            char sub = adjacentKey(c);
            return w.substring(0, i) + sub + w.substring(i + 1);
        }
        if (r < 0.50) {
            return w.substring(0, i) + w.substring(i + 1);
        }
        if (r < 0.62) {
            return w.substring(0, i) + c + w.substring(i);
        }
        if (r < 0.77 && i + 1 < w.length()) {
            return w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2);
        }
        String lower = w.toLowerCase(Locale.ROOT);
        String[][] rules = switch (cc) {
            case "MX" -> new String[][]{{"v", "b"}, {"b", "v"}, {"z", "s"}, {"ll", "y"}, {"ce", "se"}, {"h", ""}, {"ci", "si"}};
            case "GB" -> new String[][]{{"ph", "f"}, {"c", "k"}, {"ee", "ea"}, {"tt", "t"}, {"ss", "s"}, {"ll", "l"}};
            default -> new String[][]{{"u", "oe"}, {"j", "dj"}, {"c", "tj"}, {"i", "y"}, {"k", "q"}, {"ng", "n"}};
        };
        for (int k = 0; k < rules.length; k++) {
            String[] rule = rules[rnd.nextInt(rules.length)];
            int at = lower.indexOf(rule[0], 1);
            if (at > 0) {
                return w.substring(0, at) + rule[1] + w.substring(at + rule[0].length());
            }
        }
        return w.substring(0, i) + adjacentKey(c) + w.substring(i + 1);
    }

    private char adjacentKey(char c) {
        List<Character> adj = new ArrayList<>();
        for (char k = 'a'; k <= 'z'; k++) {
            if (Keyboard.adjacent(c, k)) {
                adj.add(k);
            }
        }
        return adj.isEmpty() ? c : adj.get(rnd.nextInt(adj.size()));
    }

    private static double[] zipf(int n, double s) {
        double[] cdf = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += 1 / Math.pow(i + 1, s);
            cdf[i] = sum;
        }
        for (int i = 0; i < n; i++) {
            cdf[i] /= sum;
        }
        return cdf;
    }

    private int sample(double[] cdf) {
        double u = rnd.nextDouble();
        for (int i = 0; i < cdf.length; i++) {
            if (u <= cdf[i]) {
                return i;
            }
        }
        return cdf.length - 1;
    }
}
