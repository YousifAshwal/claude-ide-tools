package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * A more complex class for testing various PSI element types.
 */
public class ComplexClass {

    // Fields with different modifiers
    public static final String CONSTANT = "constant_value";
    private final List<String> items;
    protected int protectedField;
    String packagePrivateField;

    // Static field
    private static int instanceCount = 0;

    // Constructor
    public ComplexClass() {
        this.items = new ArrayList<>();
        instanceCount++;
    }

    // Overloaded constructor
    public ComplexClass(List<String> initialItems) {
        this.items = new ArrayList<>(initialItems);
        instanceCount++;
    }

    // Instance methods
    public void addItem(String item) {
        items.add(item);
    }

    public String getItem(int index) {
        return items.get(index);
    }

    public int getItemCount() {
        return items.size();
    }

    // Static method
    public static int getInstanceCount() {
        return instanceCount;
    }

    // Method with multiple parameters
    public boolean replaceItem(int index, String newItem) {
        if (index >= 0 && index < items.size()) {
            items.set(index, newItem);
            return true;
        }
        return false;
    }

    // Inner class
    public class InnerClass {
        private String innerField;

        public InnerClass(String value) {
            this.innerField = value;
        }

        public String getInnerField() {
            return innerField;
        }
    }

    // Static nested class
    public static class StaticNestedClass {
        private int nestedValue;

        public StaticNestedClass(int value) {
            this.nestedValue = value;
        }

        public int getNestedValue() {
            return nestedValue;
        }
    }
}
