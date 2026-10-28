package com.practice.retofinal.domain;

import java.util.*;

/** Grafo dirigido simple: una conexión por par ordenado. */
public final class CampusMap {
    private final Map<String, Map<String, Integer>> adjacency = new LinkedHashMap<>();

    public void addCampus(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 60
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("La sede debe tener de 1 a 60 caracteres, sin controles ni espacios en los extremos.");
        if (adjacency.containsKey(name)) throw new IllegalArgumentException("La sede ya existe: " + name);
        adjacency.put(name, new LinkedHashMap<>());
    }
    public void requireCampus(String name) {
        if (!adjacency.containsKey(name)) throw new IllegalArgumentException("Sede inexistente: " + name);
    }
    public void connect(String from, String to, int cost) {
        requireCampus(from); requireCampus(to);
        if (cost < 0) throw new IllegalArgumentException("El costo no puede ser negativo.");
        adjacency.get(from).put(to, cost);
    }
    public void removeCampus(String name) {
        requireCampus(name); adjacency.remove(name);
        adjacency.values().forEach(edges -> edges.remove(name));
    }
    public void disconnect(String from, String to) {
        requireCampus(from); requireCampus(to); adjacency.get(from).remove(to);
    }
    public Set<String> campuses() { return Collections.unmodifiableSet(adjacency.keySet()); }
    public Map<String, Integer> outgoing(String name) { requireCampus(name); return Collections.unmodifiableMap(adjacency.get(name)); }
    public List<Connection> connections() {
        var result = new ArrayList<Connection>();
        adjacency.forEach((from, edges) -> edges.forEach((to, cost) -> result.add(new Connection(from, to, cost))));
        return List.copyOf(result);
    }
    public CampusMap copy() {
        var copy = new CampusMap(); campuses().forEach(copy::addCampus);
        connections().forEach(c -> copy.connect(c.from(), c.to(), c.cost())); return copy;
    }
}
