package org.example.trainstationproject.service;

import org.example.trainstationproject.model.Train;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class UzParserService {

    public List<Train> parseLvivStation() {
        List<Train> parsedTrains = new ArrayList<>();
        Map<LocalDateTime, Set<Integer>> occupiedPlatforms = new HashMap<>();

        try {
            String url = "https://www.uz.gov.ua/passengers/timetable/?station=23200&by_station=1";

            System.out.println("🌐 Спроба обійти блок через елітний UA проксі: 194.150.220.163:1082");

            // --- НАЛАШТУВАННЯ ПРОКСІ ---
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("194.150.220.163", 1082));

            Document doc = Jsoup.connect(url)
                    .proxy(proxy) // Вказуємо проксі для запиту
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .referrer("https://www.google.com/")
                    .timeout(40000) // Безкоштовні проксі часто повільні
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

            System.out.println("📊 Отримано рядків через проксі: " + rows.size());

            if (rows.isEmpty()) {
                System.out.println("⚠️ Увага: Проксі спрацював, але таблиця порожня. Можливо, IP вже в бані.");
            }

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

                            // --- РОЗПОДІЛ КОЛІЙ ---
                            occupiedPlatforms.putIfAbsent(departureDateTime, new HashSet<>());
                            Set<Integer> busyTodayAtThisTime = occupiedPlatforms.get(departureDateTime);

                            int platform;
                            int attempts = 0;
                            do {
                                platform = r.nextInt(10) + 1;
                                attempts++;
                            } while (busyTodayAtThisTime.contains(platform) && attempts < 15);

                            busyTodayAtThisTime.add(platform);

                            parsedTrains.add(new Train(
                                    number, route, departureDateTime,
                                    platform + " колія", type, 0
                            ));

                        } catch (Exception e) {
                            // Пропускаємо помилки формату
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка підключення через проксі: " + e.getMessage());
        }

        System.out.println("🏁 Парсинг завершено. Знайдено: " + parsedTrains.size());
        return parsedTrains;
    }
}