package com.practice.retofinal.domain;

public record Connection(String from, String to, int cost) {
    public Connection {
        if (cost < 0) throw new IllegalArgumentException("El costo no puede ser negativo.");
    }
    @Override public String toString() { return from + " → " + to + "   ·   " + cost; }
}
