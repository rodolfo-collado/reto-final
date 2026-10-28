package com.practice.retofinal.service;

import com.practice.retofinal.domain.*;
import java.util.*;

public final class DijkstraService {
    private record Entry(String campus, long distance) {}

    /** Cola con entradas diferidas: O((V+E) log(V+E)) tiempo, O(V+E) memoria. */
    public RouteResult calculate(CampusMap map, String from, String to) {
        map.requireCampus(from); map.requireCampus(to);
        var distances = new HashMap<String, Long>();
        var previous = new HashMap<String, String>();
        var queue = new PriorityQueue<Entry>(Comparator.comparingLong(Entry::distance).thenComparing(Entry::campus));
        distances.put(from, 0L); queue.add(new Entry(from, 0));
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (current.distance() != distances.getOrDefault(current.campus(), Long.MAX_VALUE)) continue;
            if (current.campus().equals(to)) {
                var route = new LinkedList<String>();
                for (String node = to; node != null; node = previous.get(node)) route.addFirst(node);
                return RouteResult.found(route, current.distance());
            }
            for (var edge : map.outgoing(current.campus()).entrySet()) {
                // Protección adicional aunque un camino simple con costos int cabe en long.
                if (current.distance() > Long.MAX_VALUE - edge.getValue()) continue;
                long candidate = current.distance() + edge.getValue();
                if (candidate < distances.getOrDefault(edge.getKey(), Long.MAX_VALUE)) {
                    distances.put(edge.getKey(), candidate); previous.put(edge.getKey(), current.campus());
                    queue.add(new Entry(edge.getKey(), candidate));
                }
            }
        }
        return RouteResult.missing();
    }
}
