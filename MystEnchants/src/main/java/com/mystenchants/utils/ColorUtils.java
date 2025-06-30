package com.mystenchants.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for handling color codes and text formatting
 */
public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Translates color codes in a string
     * Supports both legacy (&a, &b, etc.) and hex (&#FFFFFF) colors
     */
    public static String color(String text) {
        if (text == null) return null;

        // Handle hex colors first
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + hexColor.charAt(0) + "§" + hexColor.charAt(1)
                    + "§" + hexColor.charAt(2) + "§" + hexColor.charAt(3) + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5));
        }
        matcher.appendTail(buffer);

        // Handle legacy colors
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Translates color codes in a list of strings
     */
    public static List<String> color(List<String> list) {
        if (list == null) return null;
        return list.stream().map(ColorUtils::color).collect(Collectors.toList());
    }

    /**
     * Strips all color codes from a string
     */
    public static String stripColor(String text) {
        if (text == null) return null;
        return ChatColor.stripColor(color(text));
    }

    /**
     * Creates a Component from a colored string
     */
    public static Component component(String text) {
        if (text == null) return Component.empty();
        return SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Creates a list of Components from colored strings
     */
    public static List<Component> components(List<String> list) {
        if (list == null) return List.of();
        return list.stream().map(ColorUtils::component).collect(Collectors.toList());
    }

    /**
     * Formats a progress bar
     */
    public static String progressBar(double percentage, int length, String completedChar, String incompleteChar) {
        if (percentage < 0) percentage = 0;
        if (percentage > 100) percentage = 100;

        int completed = (int) (length * (percentage / 100.0));
        int incomplete = length - completed;

        StringBuilder bar = new StringBuilder();
        bar.append("&a");
        for (int i = 0; i < completed; i++) {
            bar.append(completedChar);
        }
        bar.append("&7");
        for (int i = 0; i < incomplete; i++) {
            bar.append(incompleteChar);
        }

        return color(bar.toString());
    }

    /**
     * Formats numbers with appropriate suffixes (K, M, B, etc.)
     */
    public static String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1000000) return String.format("%.1fK", number / 1000.0);
        if (number < 1000000000) return String.format("%.1fM", number / 1000000.0);
        return String.format("%.1fB", number / 1000000000.0);
    }

    /**
     * Formats time in a human-readable format
     */
    public static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m" + (remainingSeconds > 0 ? " " + remainingSeconds + "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return hours + "h" + (remainingMinutes > 0 ? " " + remainingMinutes + "m" : "");
        } else {
            long days = seconds / 86400;
            long remainingHours = (seconds % 86400) / 3600;
            return days + "d" + (remainingHours > 0 ? " " + remainingHours + "h" : "");
        }
    }

    /**
     * Centers text with a specified width
     */
    public static String centerText(String text, int width) {
        if (text == null || text.length() >= width) return text;

        int spaces = (width - stripColor(text).length()) / 2;
        StringBuilder centered = new StringBuilder();

        for (int i = 0; i < spaces; i++) {
            centered.append(" ");
        }
        centered.append(text);

        return centered.toString();
    }

    /**
     * Replaces placeholders in text
     */
    public static String replacePlaceholders(String text, String... replacements) {
        if (text == null) return null;

        String result = text;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }

        return result;
    }
}