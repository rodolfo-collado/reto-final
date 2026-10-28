package com.practice.retofinal.persistence;

import com.practice.retofinal.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class RouteHistoryRepository {
    private static final String HEADER = "CALCULOS_SEDES\t1";
    public List<RouteCalculation> load(Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) throw new IOException("Historial inválido.");
        var history = new ArrayList<RouteCalculation>();
        for (int i=1; i<lines.size(); i++) {
            try {
                var f = lines.get(i).split("\t", -1);
                if (f.length < 5) throw new IllegalArgumentException("Faltan campos.");
                Instant time = Instant.parse(f[0]);
                // Reutiliza las reglas de nombres del dominio, sin exigir sedes del mapa actual.
                var validation = new CampusMap(); validation.addCampus(f[1]);
                if (!f[1].equals(f[2])) validation.addCampus(f[2]);
                RouteResult result;
                if (f[3].equals("SIN_RUTA") && f[4].isEmpty() && f.length == 5) result = RouteResult.missing();
                else if (f[3].equals("ENCONTRADA") && f.length >= 6) {
                    long cost = Long.parseLong(f[4]);
                    var route = List.copyOf(Arrays.asList(f).subList(5, f.length));
                    if (cost < 0 || !route.get(0).equals(f[1]) || !route.get(route.size()-1).equals(f[2]))
                        throw new IllegalArgumentException("Resultado inconsistente.");
                    for (String node : route) { var check = new CampusMap(); check.addCampus(node); }
                    result = RouteResult.found(route, cost);
                } else throw new IllegalArgumentException("Estado desconocido.");
                history.add(new RouteCalculation(time, f[1], f[2], result));
            } catch (RuntimeException e) { throw new IOException("Historial inválido en línea " + (i+1) + ".", e); }
        }
        return List.copyOf(history);
    }
    public void save(Path path, List<RouteCalculation> history) throws IOException {
        var lines = new ArrayList<String>(); lines.add(HEADER);
        for (var item : history) {
            var result = item.result();
            String line = item.timestamp() + "\t" + item.from() + "\t" + item.to() + "\t" + result.status() + "\t"
                    + (result.totalCost().isPresent() ? Long.toString(result.totalCost().getAsLong()) : "");
            if (!result.route().isEmpty()) line += "\t" + String.join("\t", result.route());
            lines.add(line);
        }
        Path target = path.toAbsolutePath(); Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".rutas-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
