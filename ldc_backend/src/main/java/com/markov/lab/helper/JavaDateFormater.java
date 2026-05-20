package com.markov.lab.helper;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.time.LocalDate;
import java.time.ZoneId;

public class JavaDateFormater {
    public static LocalDate formatDate(String input_date) throws ParseException {
        if (input_date == null || input_date.isBlank()) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.parse(input_date).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
}
