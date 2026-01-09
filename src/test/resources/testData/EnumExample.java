package com.example;

/**
 * An enum for testing enum elements location.
 */
public enum EnumExample {

    FIRST("First Value", 1),
    SECOND("Second Value", 2),
    THIRD("Third Value", 3);

    private final String description;
    private final int order;

    EnumExample(String description, int order) {
        this.description = description;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }

    public int getOrder() {
        return order;
    }

    public static EnumExample fromOrder(int order) {
        for (EnumExample value : values()) {
            if (value.order == order) {
                return value;
            }
        }
        return null;
    }
}
