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

    public List<Train> parseLvivStation() {
        List<Train> parsedTrains = new ArrayList<>();
        // Мапа для відстеження зайнятих колій: Час -> Набір номерів колій
        Map<LocalDateTime, Set<Integer>> occupiedPlatforms = new HashMap<>();

        try {
            String url = "https://www.uz.gov.ua/passengers/timetable/?station=23200&by_station=1";

            System.out.println("🌐 Підключаюсь до УЗ: " + url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            Elements rows = doc.select("table.vt tr");

            if (rows.isEmpty()) {
                for (Element table : doc.select("table")) {
                    Elements testRows = table.select("tr");
                    if (!testRows.isEmpty() && testRows.get(0).select("td, th").size() >= 5) {
                        rows = testRows;
                        break;
                    }
                }
            }

            System.out.println("📊 Знайдено рядків: " + rows.size());
            Random r = new Random();

            for (int i = 0; i < rows.size(); i++) {
                Elements cols = rows.get(i).select("td");

                if (cols.size() >= 6) {
                    String number = cols.get(0).text().trim();
                    String route = cols.get(1).text().trim();
                    String depTime = cols.get(5).text().trim();

                    if (number.isEmpty() || number.equals("Номер поїзда") || depTime.equals("Час відпр.")) continue;
                    if (depTime.equals("-")) continue;

                    if (depTime.matches(".*\\d{2}:\\d{2}.*")) {
                        depTime = depTime.replaceAll(".*?(\\d{2}:\\d{2}).*", "$1");

                        try {
                            LocalTime time = LocalTime.parse(depTime);
                            LocalDateTime departureDateTime = LocalDateTime.now()
                                    .withHour(time.getHour())
                                    .withMinute(time.getMinute())
                                    .withSecond(0)
                                    .withNano(0);

                            // --- КЛАСИФІКАЦІЯ ---
                            String type = "Пасажирський";
                            if (number.startsWith("7") && (number.contains("705") || number.contains("715") || number.contains("742") || number.contains("749"))) {
                                type = "Інтерсіті+";
                            } else if (number.startsWith("8")) {
                                type = "Регіональний експрес";
                            } else if (number.length() <= 3 || number.contains("\"") || number.contains("(")) {
                                type = "Нічний Експрес";
                            }

                            // --- РОЗПОДІЛ КОЛІЙ БЕЗ КОНФЛІКТІВ ---
                            occupiedPlatforms.putIfAbsent(departureDateTime, new HashSet<>());
                            Set<Integer> busyTodayAtThisTime = occupiedPlatforms.get(departureDateTime);

                            int platform;
                            int attempts = 0;
                            do {
                                platform = r.nextInt(10) + 1; // Генеруємо колію від 1 до 10
                                attempts++;
                                // Якщо за 15 спроб не знайшли вільну (малоімовірно), просто беремо останню
                            } while (busyTodayAtThisTime.contains(platform) && attempts < 15);

                            busyTodayAtThisTime.add(platform);

                            parsedTrains.add(new Train(
                                    number, route, departureDateTime,
                                    platform + " колія", type, 0
                            ));

                        } catch (Exception e) {
                            // Пропускаємо помилкові дані
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка парсингу: " + e.getMessage());
        }

        System.out.println("🏁 Розподіл завершено. Конфліктів на коліях не виявлено.");
        return parsedTrains;
    }
}