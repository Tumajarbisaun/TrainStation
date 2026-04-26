package org.example.trainstationproject.service;

import org.example.trainstationproject.model.Train;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class UzParserService {

    /**
     * Парсинг розкладу по будь-якій станції з використанням poizdato.net.
     */
    public List<Train> parseStation(String citySlug) {
        List<Train> parsedTrains = new ArrayList<>();
        Map<LocalDateTime, Set<Integer>> occupiedPlatforms = new HashMap<>();

        try {
            // Динамічний URL на основі обраного міста
            String url = "https://poizdato.net/rozklad-po-stantsii/" + citySlug + "/";
            System.out.println("🌐 Підключаюсь до Poezdato (" + citySlug + "): " + url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(25000)
                    .get();

            Elements rows = doc.select("tr:has(td)");
            Random r = new Random();

            for (Element row : rows) {
                Elements cols = row.select("td");

                if (cols.size() >= 6) {
                    String number = cols.get(1).text().trim();
                    String route = cols.get(2).text().trim();
                    String depTimeRaw = cols.get(5).text().trim();

                    if (depTimeRaw.matches("\\d{2}[.:]\\d{2}")) {
                        String timeStr = depTimeRaw.replace(".", ":");

                        try {
                            LocalTime time = LocalTime.parse(timeStr);
                            LocalDateTime ldt = LocalDateTime.now()
                                    .withHour(time.getHour())
                                    .withMinute(time.getMinute())
                                    .withSecond(0).withNano(0);

                            String type = "Пасажирський";
                            if (number.toUpperCase().contains("ІНТЕРСІТІ") || number.startsWith("7")) {
                                type = "Інтерсіті+";
                            } else if (number.length() > 4) {
                                type = "Приміський";
                            } else if (number.length() <= 3) {
                                type = "Нічний Експрес";
                            }

                            occupiedPlatforms.putIfAbsent(ldt, new HashSet<>());
                            Set<Integer> busy = occupiedPlatforms.get(ldt);
                            int platform;
                            int attempts = 0;
                            do {
                                platform = r.nextInt(10) + 1;
                                attempts++;
                            } while (busy.contains(platform) && attempts < 15);
                            busy.add(platform);

                            parsedTrains.add(new Train(number, route, ldt, platform + " колія", type, 0));
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка під час парсингу: " + e.getMessage());
        }

        parsedTrains.sort(Comparator.comparing(Train::getActualDepartureTime));
        return parsedTrains;
    }
}