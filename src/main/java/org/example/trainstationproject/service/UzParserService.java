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
     * Парсинг розкладу по станції Львів з використанням poizdato.net.
     * Оптимізовано для роботи як локально, так і на Railway.
     */
    public List<Train> parseLvivStation() {
        List<Train> parsedTrains = new ArrayList<>();
        Map<LocalDateTime, Set<Integer>> occupiedPlatforms = new HashMap<>();

        try {
            String url = "https://poizdato.net/rozklad-po-stantsii/lviv/";
            System.out.println("🌐 Підключаюсь до Poezdato: " + url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(25000)
                    .get();

            // Вибираємо всі рядки таблиці, що містять дані
            Elements rows = doc.select("tr:has(td)");
            System.out.println("📊 Успішно отримано рядків: " + rows.size());

            Random r = new Random();

            for (Element row : rows) {
                Elements cols = row.select("td");

                // Перевіряємо структуру (на основі дебагу: Номер в Col 1, Маршрут в Col 2, Час в Col 5)
                if (cols.size() >= 6) {
                    String number = cols.get(1).text().trim();
                    String route = cols.get(2).text().trim();
                    String depTimeRaw = cols.get(5).text().trim();

                    // Обробка форматів HH.mm та HH:mm
                    if (depTimeRaw.matches("\\d{2}[.:]\\d{2}")) {
                        String timeStr = depTimeRaw.replace(".", ":");

                        try {
                            LocalTime time = LocalTime.parse(timeStr);
                            LocalDateTime ldt = LocalDateTime.now()
                                    .withHour(time.getHour())
                                    .withMinute(time.getMinute())
                                    .withSecond(0).withNano(0);

                            // Класифікація поїздів
                            String type = "Пасажирський";
                            if (number.toUpperCase().contains("ІНТЕРСІТІ") || number.startsWith("7")) {
                                type = "Інтерсіті+";
                            } else if (number.length() > 4) {
                                type = "Приміський";
                            } else if (number.length() <= 3) {
                                type = "Нічний Експрес";
                            }

                            // Автоматичний розподіл колій без конфліктів
                            occupiedPlatforms.putIfAbsent(ldt, new HashSet<>());
                            Set<Integer> busy = occupiedPlatforms.get(ldt);
                            int platform;
                            int attempts = 0;
                            do {
                                platform = r.nextInt(10) + 1;
                                attempts++;
                            } while (busy.contains(platform) && attempts < 15);
                            busy.add(platform);

                            parsedTrains.add(new Train(
                                    number,
                                    route,
                                    ldt,
                                    platform + " колія",
                                    type,
                                    0
                            ));
                        } catch (Exception e) {
                            // Пропуск некоректних записів часу
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка під час парсингу: " + e.getMessage());
        }

        // Сортування для хронологічного відображення на табло
        parsedTrains.sort(Comparator.comparing(Train::getActualDepartureTime));

        System.out.println("🏁 Парсинг завершено! Відображено на табло: " + parsedTrains.size());
        return parsedTrains;
    }
}