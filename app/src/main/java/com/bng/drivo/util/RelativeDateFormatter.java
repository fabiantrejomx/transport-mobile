package com.bng.drivo.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** "Hoy, 14:30" / "Ayer, 09:15" / "Lun, 09:02" a partir de un timestamp ISO-8601 del servidor. */
public final class RelativeDateFormatter {

    private static final String[] DAY_ABBREVIATIONS = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private RelativeDateFormatter() {
    }

    public static String format(String isoTimestamp) {
        if (isoTimestamp == null) {
            return "";
        }
        try {
            ZonedDateTime dateTime = Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault());
            LocalDate date = dateTime.toLocalDate();
            LocalDate today = LocalDate.now();
            String time = TIME_FORMAT.format(dateTime);

            if (date.equals(today)) {
                return "Hoy, " + time;
            } else if (date.equals(today.minusDays(1))) {
                return "Ayer, " + time;
            } else {
                return DAY_ABBREVIATIONS[dateTime.getDayOfWeek().getValue() % 7] + ", " + time;
            }
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
