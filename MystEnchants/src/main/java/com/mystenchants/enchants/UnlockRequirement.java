package com.mystenchants.enchants;

import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced UnlockRequirement with additional properties support
 * Represents an unlock requirement for an enchant level with full configurability
 */
public class UnlockRequirement {

    private final RequirementType type;
    private final long amount;
    private final String message;
    private final Map<String, Object> additionalProperties;

    public UnlockRequirement(RequirementType type, long amount, String message) {
        this(type, amount, message, new HashMap<>());
    }

    public UnlockRequirement(RequirementType type, long amount, String message, Map<String, Object> additionalProperties) {
        this.type = type;
        this.amount = amount;
        this.message = message;
        this.additionalProperties = additionalProperties != null ? additionalProperties : new HashMap<>();
    }

    public RequirementType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public Object getProperty(String key) {
        return additionalProperties.get(key);
    }

    public String getStringProperty(String key, String defaultValue) {
        Object value = additionalProperties.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    public int getIntProperty(String key, int defaultValue) {
        Object value = additionalProperties.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    public double getDoubleProperty(String key, double defaultValue) {
        Object value = additionalProperties.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        Object value = additionalProperties.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public String getFormattedMessage(long current) {
        return message.replace("{current}", String.valueOf(current))
                .replace("{required}", String.valueOf(amount))
                .replace("{amount}", String.valueOf(amount));
    }

    public boolean isMet(long current) {
        return current >= amount;
    }

    public double getProgress(long current) {
        if (amount <= 0) return 100.0;
        return Math.min(100.0, (current * 100.0) / amount);
    }

    public long getRemaining(long current) {
        return Math.max(0, amount - current);
    }

    public boolean isProgressBased() {
        return type.requiresStatistics() || type.isCurrency();
    }

    public boolean isInstant() {
        return type == RequirementType.NONE || type == RequirementType.BOSS_FIGHT || type == RequirementType.TBD;
    }

    @Override
    public String toString() {
        return "UnlockRequirement{" +
                "type=" + type +
                ", amount=" + amount +
                ", message='" + message + '\'' +
                '}';
    }
}