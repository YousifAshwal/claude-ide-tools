package com.example;

/**
 * An interface for testing interface elements location.
 */
public interface InterfaceExample {

    // Constant
    String INTERFACE_CONSTANT = "interface_value";

    // Abstract method
    void performAction();

    // Method with parameter
    String process(String input);

    // Default method
    default String getDescription() {
        return "Default implementation";
    }

    // Static method
    static InterfaceExample createDefault() {
        return new InterfaceExample() {
            @Override
            public void performAction() {
                System.out.println("Action performed");
            }

            @Override
            public String process(String input) {
                return input.toUpperCase();
            }
        };
    }
}
