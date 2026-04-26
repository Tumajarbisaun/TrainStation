package org.example.trainstationproject.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Train {
    private String number;
    private String destination;
    private LocalDateTime departureDateTime;
    private String platform;
    private String type;
    private int delayMinutes;

    public LocalDateTime getActualDepartureTime() {
        return departureDateTime.plusMinutes(delayMinutes);
    }

    public boolean isActual() {
        return getActualDepartureTime().isAfter(LocalDateTime.now());
    }

    public String getFormattedDateTime() {
        return departureDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    public String getStatus() {
        long min = ChronoUnit.MINUTES.between(LocalDateTime.now(), getActualDepartureTime());
        String delayText = delayMinutes > 0 ? " [+" + delayMinutes + "]" : "";

        if (min < 0) return "🏁 Відправлено";
        if (min <= 15) return "⚠️ ПОСАДКА (" + min + "хв)" + delayText;
        return "✅ Очікується" + delayText;
    }
}