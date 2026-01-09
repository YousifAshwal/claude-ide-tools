package com.example;

/**
 * A class that references SimpleClass for testing reference resolution.
 */
public class ClassWithReferences {

    private SimpleClass simpleClass;

    public ClassWithReferences() {
        this.simpleClass = new SimpleClass("referenced");
    }

    public String getSimpleClassName() {
        return simpleClass.getName();
    }

    public void updateName(String newName) {
        simpleClass.setName(newName);
    }

    public void incrementAndPrint() {
        simpleClass.incrementCount();
        int currentCount = simpleClass.getCount();
        System.out.println("Count: " + currentCount);
    }
}
