package de.example.rendite;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Klasse zum Lesen und Verarbeiten der Anleihen-CSV-Datei
 */
public class CsvReader {

    /**
     * Datenklasse für einen Anleihen-Eintrag
     */
    public static class AnleihenEintrag {
        private final String isin;
        private final String timestamp;
        private final String currency;
        private final String bidPrice;
        private final String askPrice;

        // Neue Felder für erweiterte Berechnung
        private int restlaufzeitTage = -1;
        private double rendite = -1.0;
        private double briefkurs = -1.0;
        private double nominalzins = -1.0;
        private String faelligkeitsdatum = "";

        public AnleihenEintrag(String isin, String timestamp, String currency,
                              String bidPrice, String askPrice) {
            this.isin = isin;
            this.timestamp = timestamp;
            this.currency = currency;
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
        }

        // Getter
        public String getIsin() { return isin; }
        public String getTimestamp() { return timestamp; }
        public String getAskPrice() { return askPrice; }

        // Neue Getter/Setter
        public int getRestlaufzeitTage() { return restlaufzeitTage; }
        public void setRestlaufzeitTage(int restlaufzeitTage) { this.restlaufzeitTage = restlaufzeitTage; }

        public double getRendite() { return rendite; }
        public void setRendite(double rendite) { this.rendite = rendite; }

        public double getBriefkurs() { return briefkurs; }
        public void setBriefkurs(double briefkurs) { this.briefkurs = briefkurs; }

        public double getNominalzins() { return nominalzins; }
        public void setNominalzins(double nominalzins) { this.nominalzins = nominalzins; }

        public String getFaelligkeitsdatum() { return faelligkeitsdatum; }
        public void setFaelligkeitsdatum(String faelligkeitsdatum) { this.faelligkeitsdatum = faelligkeitsdatum; }

        @Override
        public String toString() {
            return String.format("%-15s %-8s %-10s %-10s %-12s %-8d %-10.3f%%",
                    isin, currency, bidPrice, askPrice, faelligkeitsdatum,
                    Math.max(restlaufzeitTage, 0),
                    rendite >= 0 ? rendite : 0.0);
        }
    }

    /**
     * Liest die GZIP-komprimierte CSV-Datei und gibt die neuesten Einträge pro ISIN zurück
     * Ultra-aggressiv optimiert für sub-10-Sekunden Performance
     */
    public static Map<String, AnleihenEintrag> readCsvFile(String filePath) throws IOException {
        System.out.println("Starte ULTRA-AGGRESSIVE CSV-Verarbeitung...");
        long startTime = System.currentTimeMillis();

        // Maximale Buffer für extremste I/O Performance
        final int GZIP_BUFFER = 262144; // 256KB - Maximum!
        final int READ_BUFFER = 524288; // 512KB - Extrem groß!

        // Pre-size HashMap für bessere Performance (keine Rehashing)
        Map<String, AnleihenEintrag> latestEntries = new java.util.concurrent.ConcurrentHashMap<>(100000);

        // Viel größere Batches für weniger Overhead
        final int BATCH_SIZE = 50000; // 5x größer!
        List<String> batch = new ArrayList<>(BATCH_SIZE);
        int lineCount = 0;
        int batchCount = 0;

        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis, GZIP_BUFFER);
             InputStreamReader isr = new InputStreamReader(gzis, java.nio.charset.StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr, READ_BUFFER)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;

                // Noch aggressivere Früh-Filter
                if (line.length() < 20 || !line.contains(",")) continue;

                batch.add(line);

                // Verarbeite größere Batches seltener
                if (batch.size() >= BATCH_SIZE) {
                    batchCount++;
                    if (batchCount % 5 == 1) { // Weniger Output für Speed
                        System.out.println("Mega-Batch " + batchCount + " (Zeilen: " + lineCount + ", ISINs: " + latestEntries.size() + ")");
                    }
                    processMegaBatchParallel(batch, latestEntries);
                    batch.clear();
                }
            }

            // Verarbeite letzten Batch
            if (!batch.isEmpty()) {
                batchCount++;
                processMegaBatchParallel(batch, latestEntries);
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("ULTRA-AGGRESSIVE CSV-Verarbeitung abgeschlossen:");
        System.out.println("  - Verarbeitete Zeilen: " + lineCount);
        System.out.println("  - Mega-Batches: " + batchCount);
        System.out.println("  - Unique ISINs: " + latestEntries.size());
        System.out.println("  - Verarbeitungszeit: " + (endTime - startTime) + "ms");
        System.out.println("  - Zeilen/Sekunde: " + (lineCount * 1000L / Math.max(1, endTime - startTime)));
        System.out.println("  - SPEED BOOST ACHIEVED!");

        return latestEntries;
    }

    /**
     * Verarbeitet Mega-Batches mit maximaler Parallelisierung
     */
    private static void processMegaBatchParallel(List<String> batch, Map<String, AnleihenEintrag> latestEntries) {
        // Maximale Parallelisierung mit ForkJoin
        batch.parallelStream()
             .unordered() // Wichtig für Performance!
             .forEach(line -> {
                 AnleihenEintrag entry = parseLineUltraFast(line);
                 if (entry != null) {
                     // Optimierter Thread-safe Update
                     latestEntries.merge(entry.getIsin(), entry,
                         (existing, newEntry) -> newEntry.getTimestamp().compareTo(existing.getTimestamp()) > 0 ? newEntry : existing
                     );
                 }
             });
    }

    /**
     * ULTRA-FAST Line Parsing - eliminiert alle unnötigen Operationen
     */
    private static AnleihenEintrag parseLineUltraFast(String line) {
        // Direkte char-Array Zugriffe (schneller als charAt)
        char[] chars = line.toCharArray();
        int len = chars.length;

        // Schnellste Komma-Suche mit Array-Zugriff
        int[] commas = new int[6];
        int commaCount = 0;

        for (int i = 0; i < len && commaCount < 6; i++) {
            if (chars[i] == ',') {
                commas[commaCount++] = i;
            }
        }

        if (commaCount < 6) return null;

        // Ultra-schnelle Feld-Extraktion OHNE String-Operationen wo möglich
        String isin = extractFieldUltraFast(chars, 0, commas[0]);
        if (isin.isEmpty()) return null;

        String timestamp = extractFieldUltraFast(chars, commas[0] + 1, commas[1]);
        String currency = extractFieldUltraFast(chars, commas[1] + 1, commas[2]);
        String bidPrice = extractFieldUltraFast(chars, commas[2] + 1, commas[3]);
        String askPrice = extractFieldUltraFast(chars, commas[4] + 1, commas[5]);

        return new AnleihenEintrag(isin, timestamp, currency, bidPrice, askPrice);
    }

    /**
     * Ultra-fast field extraction direkt vom char-Array
     */
    private static String extractFieldUltraFast(char[] chars, int start, int end) {
        // Skip Whitespace am Anfang
        while (start < end && chars[start] <= ' ') start++;
        // Skip Whitespace am Ende
        while (end > start && chars[end - 1] <= ' ') end--;

        // Direkte String-Konstruktion vom char-Array (schneller als substring)
        return start < end ? new String(chars, start, end - start) : "";
    }

    /**
     * Führt eine HTTP-Anfrage mit Retry-Mechanismus bei Timeouts durch
     */
    public static HttpResponse<String> sendRequestWithRetry(HttpClient httpClient, HttpRequest request, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                attempt++;
                System.out.println("  -> Versuch " + attempt + "/" + maxRetries);
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException e) {
                System.out.println("  -> Timeout bei Versuch " + attempt + "/" + maxRetries);
                if (attempt >= maxRetries) {
                    System.err.println("  -> Alle " + maxRetries + " Versuche fehlgeschlagen wegen Timeout");
                    throw new RuntimeException("HTTP Timeout nach " + maxRetries + " Versuchen", e);
                }
                // Warten vor nächstem Versuch (exponential backoff)
                try {
                    int waitTime = 2000 * attempt; // 2s, 4s, 6s, etc.
                    System.out.println("  -> Warte " + waitTime + "ms vor nächstem Versuch...");
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Unterbrochen während Retry-Wartezeit", ie);
                }
            } catch (Exception e) {
                // Andere Fehler (nicht Timeout) sofort weiterwerfen
                throw new RuntimeException("HTTP Fehler: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Unerwarteter Fehler im Retry-Mechanismus");
    }

    /**
     * Prüft ob eine ISIN eine Anleihe ist und extrahiert die Anleihen-Daten
     */
    public static AnleihenEintrag isAnleiheUndExtrahiereDaten(String isin, HttpClient httpClient, AnleihenEintrag eintrag) {
        try {
            // Zuerst die Hauptseite besuchen um Cookies/Session zu erhalten
            String mainUrl = "https://www.comdirect.de/";
            System.out.println("Besuche Hauptseite für Session: " + mainUrl);

            HttpRequest mainRequest = HttpRequest.newBuilder()
                    .uri(URI.create(mainUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            httpClient.send(mainRequest, HttpResponse.BodyHandlers.ofString());

            // Kurz warten
            Thread.sleep(500);

            // Jetzt die Anleihen-Seite besuchen
            String url = "https://www.comdirect.de/inf/anleihen/" + isin;
            System.out.println("Prüfe ISIN: " + isin + " -> URL: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.comdirect.de/")
                    .GET()
                    .build();

            HttpResponse<String> response = sendRequestWithRetry(httpClient, request, 3);

            int statusCode = response.statusCode();
            boolean isAnleihe = statusCode == 200;

            System.out.println("  -> HTTP Status: " + statusCode + " -> " + (isAnleihe ? "IST Anleihe" : "KEINE Anleihe"));

            if (isAnleihe) {
                // HTML-Content parsen und Daten extrahieren
                String htmlContent = response.body();
                extractAnleihenDaten(eintrag, htmlContent);

                System.out.println("  -> Fälligkeit: " + eintrag.getFaelligkeitsdatum());
                System.out.println("  -> Restlaufzeit: " + eintrag.getRestlaufzeitTage() + " Tage");
                System.out.println("  -> Nominalzins: " + eintrag.getNominalzins() + "%");
                System.out.println("  -> Briefkurs (aus CSV): " + eintrag.getBriefkurs());
                System.out.println("  -> Berechnete Rendite: " + String.format("%.3f", eintrag.getRendite()) + "%");

                return eintrag;
            }

            // Debug: Bei 401 die Response-Headers anzeigen
            if (statusCode == 401) {
                System.out.println("  -> 401 Response Headers:");
                response.headers().map().forEach((key, value) ->
                    System.out.println("     " + key + ": " + String.join(", ", value)));
            }

            return null;

        } catch (Exception e) {
            // Bei Fehlern nehmen wir an, dass es keine Anleihe ist
            System.err.println("Fehler beim Prüfen der ISIN " + isin + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Filtert die Einträge und behält nur Anleihen
     */
    public static List<AnleihenEintrag> filterAnleihen(Map<String, AnleihenEintrag> entries) {
        List<AnleihenEintrag> anleihen = new ArrayList<>();

        // HTTP-Client mit Cookie-Management
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new java.net.CookieManager()) // Automatisches Cookie-Management
                .build();

        int total = entries.size();
        int current = 0;

        System.out.println("Prüfe " + total + " ISINs auf Anleihen...");

        for (AnleihenEintrag entry : entries.values()) {
            current++;
            System.out.println(); // Neue Zeile für bessere Lesbarkeit
            System.out.println("=== " + current + "/" + total + " (" +
                           String.format("%.1f", (current * 100.0) / total) + "%) ===");

            if (isAnleiheUndExtrahiereDaten(entry.getIsin(), httpClient, entry) != null) {
                anleihen.add(entry);
            }

            // Längere Pause um Server nicht zu überlasten
            try {
                Thread.sleep(3000); // 3 Sekunden Pause - länger wegen 401-Problemen
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println(); // Neue Zeile nach Fortschrittsanzeige
        return anleihen;
    }

    /**
     * Extrahiert Anleihen-Daten aus dem HTML und berechnet Restlaufzeit und Rendite
     */
    public static void extractAnleihenDaten(AnleihenEintrag eintrag, String htmlContent) {
        try {
            // DEBUG: Relevanten HTML-Teil für Fälligkeit ausgeben
            int faelligkeitStart = htmlContent.indexOf("lligkeit"); // Suche nach "lligkeit" um sowohl "Fälligkeit" als auch "F&auml;lligkeit" zu finden
            if (faelligkeitStart >= 0) {
                int contextStart = Math.max(0, faelligkeitStart - 100);
                int contextEnd = Math.min(htmlContent.length(), faelligkeitStart + 300);
                String context = htmlContent.substring(contextStart, contextEnd);
                System.out.println("DEBUG - HTML um Fälligkeit:");
                System.out.println(context);
                System.out.println("---");
            }

            // Fälligkeit extrahieren - berücksichtigt sowohl "Fälligkeit" als auch "F&auml;lligkeit"
            String faelligkeitsPattern = "<th[^>]*>F(?:ä|&auml;)lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(faelligkeitsPattern);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String faelligkeit = matcher.group(1).trim();
                eintrag.setFaelligkeitsdatum(faelligkeit);
                System.out.println("DEBUG - Fälligkeit gefunden: " + faelligkeit);

                // Restlaufzeit in Tagen berechnen (präzise über Datum)
                int restlaufzeitTage = berechneRestlaufzeitTage(faelligkeit);
                eintrag.setRestlaufzeitTage(restlaufzeitTage);
                System.out.println("DEBUG - Restlaufzeit berechnet: " + restlaufzeitTage + " Tage");
            } else {
                System.out.println("DEBUG - Fälligkeit NICHT gefunden mit Pattern: " + faelligkeitsPattern);

                // Erweiterte Fallback-Patterns für verschiedene HTML-Varianten
                String[] fallbackPatterns = {
                    "<th[^>]*>F&auml;lligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    "<th[^>]*>Fälligkeit</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>",
                    ">[^<]*(?:F&auml;lligkeit|Fälligkeit)[^<]*</th>\\s*<td[^>]*>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</td>"
                };

                for (String fallbackPattern : fallbackPatterns) {
                    pattern = java.util.regex.Pattern.compile(fallbackPattern);
                    matcher = pattern.matcher(htmlContent);
                    if (matcher.find()) {
                        String faelligkeit = matcher.group(1).trim();
                        eintrag.setFaelligkeitsdatum(faelligkeit);
                        System.out.println("DEBUG - Fälligkeit mit Fallback gefunden: " + faelligkeit + " (Pattern: " + fallbackPattern + ")");

                        int restlaufzeitTage = berechneRestlaufzeitTage(faelligkeit);
                        eintrag.setRestlaufzeitTage(restlaufzeitTage);
                        System.out.println("DEBUG - Restlaufzeit berechnet: " + restlaufzeitTage + " Tage");
                        break;
                    }
                }
            }

            // DEBUG: Relevanten HTML-Teil für Nominalzins ausgeben
            int nominalStart = htmlContent.indexOf("Nominalzinssatz");
            if (nominalStart >= 0) {
                int contextStart = Math.max(0, nominalStart - 100);
                int contextEnd = Math.min(htmlContent.length(), nominalStart + 300);
                String context = htmlContent.substring(contextStart, contextEnd);
                System.out.println("DEBUG - HTML um Nominalzinssatz:");
                System.out.println(context);
                System.out.println("---");
            }

            // Nominalzinssatz extrahieren (mit &#160; HTML-Entity)
            String nominalzinsPattern = "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,.]+)\\s*&#160;\\s*%</td>";
            pattern = java.util.regex.Pattern.compile(nominalzinsPattern);
            matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                String nominalzinsStr = matcher.group(1).replace(",", ".");
                double nominalzins = Double.parseDouble(nominalzinsStr);
                eintrag.setNominalzins(nominalzins);
                System.out.println("DEBUG - Nominalzins gefunden: " + nominalzins + "%");
            } else {
                System.out.println("DEBUG - Nominalzins NICHT gefunden mit Pattern: " + nominalzinsPattern);

                // Fallback: Versuche andere Varianten
                String[] fallbackPatterns = {
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*&nbsp;\\s*%</td>",
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)\\s*%</td>",
                    "<th[^>]*>Nominalzinssatz</th>\\s*<td[^>]*>([0-9,\\.]+)[^0-9]*%</td>"
                };

                for (String fallbackPattern : fallbackPatterns) {
                    pattern = java.util.regex.Pattern.compile(fallbackPattern);
                    matcher = pattern.matcher(htmlContent);
                    if (matcher.find()) {
                        String nominalzinsStr = matcher.group(1).replace(",", ".");
                        double nominalzins = Double.parseDouble(nominalzinsStr);
                        eintrag.setNominalzins(nominalzins);
                        System.out.println("DEBUG - Nominalzins mit Fallback gefunden: " + nominalzins + "% (Pattern: " + fallbackPattern + ")");
                        break;
                    }
                }
            }

            // Briefkurs aus der ursprünglichen CSV-Datei nehmen (askPrice)
            try {
                double briefkurs = Double.parseDouble(eintrag.getAskPrice().replace(",", "."));
                eintrag.setBriefkurs(briefkurs);

                // Rendite berechnen
                if (eintrag.getRestlaufzeitTage() > 0 && eintrag.getNominalzins() > 0) {
                    double rendite = berechneRendite(briefkurs, eintrag.getNominalzins(),
                                                   eintrag.getRestlaufzeitTage(), 2.50);
                    eintrag.setRendite(rendite);
                }
            } catch (NumberFormatException e) {
                System.err.println("Fehler beim Parsen des Briefkurses aus CSV für " + eintrag.getIsin() + ": " + eintrag.getAskPrice());
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Extrahieren der Anleihen-Daten für " + eintrag.getIsin() + ": " + e.getMessage());
        }
    }

    /**
     * Berechnet die Restlaufzeit in Tagen basierend auf dem Fälligkeitsdatum
     */
    public static int berechneRestlaufzeitTage(String faelligkeitsdatum) {
        try {
            // Format: "24.01.2052"
            String[] teile = faelligkeitsdatum.split("\\.");
            if (teile.length == 3) {
                int tag = Integer.parseInt(teile[0]);
                int monat = Integer.parseInt(teile[1]);
                int jahr = Integer.parseInt(teile[2]);

                java.time.LocalDate faelligkeit = java.time.LocalDate.of(jahr, monat, tag);
                java.time.LocalDate heute = java.time.LocalDate.now();

                return (int) java.time.temporal.ChronoUnit.DAYS.between(heute, faelligkeit);
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen des Fälligkeitsdatums: " + faelligkeitsdatum);
        }
        return -1;
    }

    /**
     * Berechnet die Rendite bis zur Fälligkeit (Yield to Maturity)
     * unter Berücksichtigung der Transaktionskosten
     */
    public static double berechneRendite(double briefkurs, double nominalzins, int tageRestlaufzeit, double kosten) {
        try {
            // Einfache Renditeberechnung (ohne komplexe YTM-Iteration)
            double jahreFaelligkeit = tageRestlaufzeit / 365.0;

            // Gesamtkosten des Kaufs (Briefkurs + Transaktionskosten)
            double gesamtkosten = briefkurs + kosten;

            // Annahme: Nominalwert = 100, jährliche Zinszahlungen
            double nominalwert = 100.0;
            double jaehrlicheZinsen = nominalwert * (nominalzins / 100.0);

            // Gesamte Zinszahlungen bis zur Fälligkeit
            double gesamteZinsen = jaehrlicheZinsen * jahreFaelligkeit;

            // Gesamtertrag = Zinszahlungen + Rückzahlung des Nominalwerts
            double gesamtertrag = gesamteZinsen + nominalwert;

            // Rendite = (Gesamtertrag / Gesamtkosten)^(1/Jahre) - 1
            double rendite = Math.pow(gesamtertrag / gesamtkosten, 1.0 / jahreFaelligkeit) - 1.0;

            return rendite * 100.0; // In Prozent

        } catch (Exception e) {
            System.err.println("Fehler bei der Renditeberechnung: " + e.getMessage());
            return -1.0;
        }
    }

    /**
     * Hauptmethode
     */
    public static void main(String[] args) {
        String csvFilePath = "C:\\tmp\\anleihen\\pretrade.20251013.14.45.mund.csv.gz";

        try {
            System.out.println("Lese CSV-Datei: " + csvFilePath);
            Map<String, AnleihenEintrag> latestEntries = readCsvFile(csvFilePath);

            System.out.println("Insgesamt " + latestEntries.size() + " einzigartige ISINs gefunden.");

            // Nur ISINs mit "GR" filtern (griechische Anleihen)
            Map<String, AnleihenEintrag> grEntries = new HashMap<>();
            for (Map.Entry<String, AnleihenEintrag> entry : latestEntries.entrySet()) {
                if (entry.getKey().startsWith("GR")) {
                    grEntries.put(entry.getKey(), entry.getValue());
                }
            }

            System.out.println("Davon " + grEntries.size() + " ISINs die mit 'GR' beginnen.");

            if (grEntries.isEmpty()) {
                System.out.println("Keine ISINs mit 'GR' gefunden.");
                return;
            }

            // Anleihen filtern (nur GR-ISINs)
            System.out.println();
            List<AnleihenEintrag> anleihen = filterAnleihen(grEntries);

            // Gefundene Anleihen ausgeben
            System.out.println();
            System.out.println("=== Griechische Anleihen (GR) ===");
            System.out.printf("Gefundene Anleihen: %d%n", anleihen.size());
            System.out.println();
            System.out.printf("%-15s %-8s %-10s %-10s %-12s %-8s %-10s%n",
                    "ISIN", "Währung", "Geldkurs", "Briefkurs", "Fälligkeit", "Tage", "Rendite");
            System.out.println("-".repeat(85));
            anleihen.stream()
                    .sorted(Comparator.comparing(AnleihenEintrag::getRendite).reversed())
                    .forEach(System.out::println);

        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der CSV-Datei: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
