package com.example;

/**
 * A simple class for testing PSI element location.
 */
public class SimpleClass {

    private String name;
    private int count;

    public SimpleClass(String name) {
        this.name = name;
        this.count = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }

    public static void main(String[] args) {
        SimpleClass instance = new SimpleClass("test");
        System.out.println(instance.getName());
    }
}
