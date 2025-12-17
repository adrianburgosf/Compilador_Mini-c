package org.example.minic.ir;

public class TempFactory {
    private int n = 0;
    public String next() { return "t" + (++n); }
}
