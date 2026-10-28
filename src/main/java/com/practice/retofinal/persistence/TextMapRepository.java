package com.practice.retofinal.persistence;

import com.practice.retofinal.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class TextMapRepository implements MapRepository {
    private static final String HEADER = "MAPA_SEDES\t1";
    @Override public CampusMap load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) throw new IOException("Formato de mapa inválido: se esperaba MAPA_SEDES versión 1.");
        var map = new CampusMap();
        var edges = new ArrayList<String[]>();
        var pairs = new HashSet<List<String>>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] fields = lines.get(i).split("\t", -1);
            try {
                if (fields.length == 2 && fields[0].equals("SEDE")) map.addCampus(fields[1]);
                else if (fields.length == 4 && fields[0].equals("CONEXION")) edges.add(fields);
                else throw new IllegalArgumentException("Registro desconocido.");
            } catch (IllegalArgumentException e) { throw new IOException("Línea " + (i + 1) + ": " + e.getMessage(), e); }
        }
        for (String[] edge : edges) {
            try {
                if (!pairs.add(List.of(edge[1], edge[2]))) throw new IllegalArgumentException("Conexión duplicada.");
                map.connect(edge[1], edge[2], Integer.parseInt(edge[3]));
            } catch (IllegalArgumentException e) { throw new IOException("Conexión inválida " + edge[1] + " → " + edge[2] + ": " + e.getMessage(), e); }
        }
        return map;
    }
    @Override public void save(Path path, CampusMap map) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        var lines = new ArrayList<String>(); lines.add(HEADER);
        map.campuses().forEach(name -> lines.add("SEDE\t" + name));
        map.connections().forEach(c -> lines.add("CONEXION\t" + c.from() + "\t" + c.to() + "\t" + c.cost()));
        Path temporary = Files.createTempFile(target.getParent(), ".mapa-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
