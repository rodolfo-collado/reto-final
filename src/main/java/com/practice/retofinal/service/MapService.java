package com.practice.retofinal.service;

import com.practice.retofinal.domain.*;
import com.practice.retofinal.persistence.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.*;
import java.time.Instant;

/** Guarda primero y publica el cambio en memoria solo si la escritura tiene éxito. */
public final class MapService {
    private final Path storage;
    private final MapRepository repository;
    private CampusMap map;
    private final Path historyPath;
    private final RouteHistoryRepository historyRepository = new RouteHistoryRepository();
    private List<RouteCalculation> history;
    public MapService(Path storage, MapRepository repository) throws IOException {
        this.storage = storage; this.repository = repository;
        this.map = Files.exists(storage) ? repository.load(storage) : new CampusMap();
        historyPath = storage.resolveSibling(storage.getFileName() + ".rutas.tsv");
        history = historyRepository.load(historyPath);
    }
    public CampusMap snapshot() { return map.copy(); }
    public void change(Consumer<CampusMap> operation) throws IOException {
        CampusMap candidate = map.copy(); operation.accept(candidate); replace(candidate);
    }
    private void replace(CampusMap candidate) throws IOException { repository.save(storage, candidate); map = candidate; }
    public void importMap(Path source) throws IOException { replace(repository.load(source)); }
    public void exportMap(Path target) throws IOException { repository.save(target, map); }
    public List<RouteCalculation> history() { return List.copyOf(history); }
    public RouteResult calculate(String from, String to) throws IOException {
        RouteResult result = new DijkstraService().calculate(map, from, to);
        var candidate = new ArrayList<>(history);
        candidate.add(new RouteCalculation(Instant.now(), from, to, result));
        historyRepository.save(historyPath, candidate); history = List.copyOf(candidate);
        return result;
    }
}
