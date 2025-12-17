package org.example.minic.ir;

public class LabelFactory {
    private int n = 0;
    public String next() { return "L" + (++n); }
}
