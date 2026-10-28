package com.practice.retofinal;

import com.practice.retofinal.domain.*;
import com.practice.retofinal.service.*;
import com.practice.retofinal.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapTest {
    @TempDir Path temp;
    final DijkstraService algorithm = new DijkstraService();
    final TextMapRepository repository = new TextMapRepository();
    CampusMap demo() {
        var map = new CampusMap();
        List.of("A", "B", "C", "D").forEach(map::addCampus);
        map.connect("A", "B", 4); map.connect("A", "C", 1); map.connect("C", "B", 2); return map;
    }
    Path file(String text) throws IOException {
        Path path = temp.resolve("input.tsv"); Files.writeString(path, text); return path;
    }
    @Test void acceptanceShortestRoute() {
        var result = algorithm.calculate(demo(), "A", "B");
        assertEquals(RouteResult.Status.ENCONTRADA, result.status());
        assertEquals(3L, result.totalCost().orElseThrow()); assertEquals(List.of("A", "C", "B"), result.route());
    }
    @Test void isolatedCampusHasNoRoute() {
        var result = algorithm.calculate(demo(), "A", "D");
        assertEquals(RouteResult.Status.SIN_RUTA, result.status()); assertTrue(result.route().isEmpty()); assertTrue(result.totalCost().isEmpty());
    }
    @Test void negativeWeightDoesNotInsert() {
        var map = demo(); assertThrows(IllegalArgumentException.class, () -> map.connect("B", "D", -1));
        assertTrue(map.outgoing("B").isEmpty());
    }
    @Test void negativeWeightDoesNotOverwrite() {
        var map = demo(); assertThrows(IllegalArgumentException.class, () -> map.connect("A", "B", -1));
        assertEquals(4, map.outgoing("A").get("B"));
    }
    @Test void sameCampusReturnsZero() {
        var result = algorithm.calculate(demo(), "D", "D");
        assertEquals(0, result.totalCost().orElseThrow()); assertEquals(List.of("D"), result.route());
    }
    @Test void sameUnknownCampusIsRejected() { assertThrows(IllegalArgumentException.class, () -> algorithm.calculate(demo(), "X", "X")); }
    @Test void missingSourceRejected() { assertThrows(IllegalArgumentException.class, () -> algorithm.calculate(demo(), "X", "B")); }
    @Test void missingDestinationRejected() { assertThrows(IllegalArgumentException.class, () -> algorithm.calculate(demo(), "A", "X")); }
    @Test void missingConnectionEndpointRejected() {
        var map = demo(); assertThrows(IllegalArgumentException.class, () -> map.connect("X", "B", 1));
        assertThrows(IllegalArgumentException.class, () -> map.connect("A", "X", 1)); assertEquals(3, map.connections().size());
    }
    @Test void nullSelectionRejected() { assertThrows(IllegalArgumentException.class, () -> algorithm.calculate(demo(), null, "B")); }
    @Test void edgesAreDirected() { assertEquals(RouteResult.Status.SIN_RUTA, algorithm.calculate(demo(), "B", "A").status()); }
    @Test void longDistancesDoNotOverflowInt() {
        var map = demo(); map.connect("A", "B", Integer.MAX_VALUE); map.disconnect("A", "C"); map.connect("B", "D", Integer.MAX_VALUE);
        assertEquals(4294967294L, algorithm.calculate(map, "A", "D").totalCost().orElseThrow());
    }
    @Test void zeroCostCycleTerminatesAndHasValidRoute() {
        var map = demo(); map.connect("A", "C", 0); map.connect("C", "A", 0); map.connect("C", "B", 0);
        var result = algorithm.calculate(map, "A", "B"); assertEquals(0, result.totalCost().orElseThrow()); assertEquals(List.of("A","C","B"), result.route());
    }
    @Test void selfLoopWorks() {
        var map = demo(); map.connect("A", "A", 0); assertEquals(3, algorithm.calculate(map, "A", "B").totalCost().orElseThrow());
    }
    @Test void staleQueueEntriesAreIgnored() {
        var map = demo(); map.connect("B", "D", 10); assertEquals(13, algorithm.calculate(map, "A", "D").totalCost().orElseThrow());
    }
    @Test void equalRoutesReturnValidCost() {
        var map = demo(); map.connect("A", "B", 3); var result = algorithm.calculate(map, "A", "B");
        long total = 0; for (int i=1;i<result.route().size();i++) total += map.outgoing(result.route().get(i-1)).get(result.route().get(i));
        assertEquals(3, total); assertEquals(total, result.totalCost().orElseThrow());
    }
    @Test void invalidAndDuplicateCampusRejected() {
        var map = demo();
        for (String name : Arrays.asList(null,"", "  ", " A", "A\tB", "A\nB", "a".repeat(61), "A"))
            assertThrows(IllegalArgumentException.class, () -> map.addCampus(name));
        assertEquals(4, map.campuses().size());
    }
    @Test void campusRemovalCleansIncomingAndOutgoingEdges() {
        var map = demo(); map.removeCampus("C"); assertEquals(List.of(new Connection("A","B",4)), map.connections());
    }
    @Test void roundTripPreservesUnicodeIsolatedNodesAndMaxCost() throws IOException {
        var map = demo(); map.addCampus("Sede León"); map.connect("B", "Sede León", Integer.MAX_VALUE);
        Path path = temp.resolve("sub/mapa.tsv"); repository.save(path, map); var loaded = repository.load(path);
        assertEquals(map.campuses(), loaded.campuses()); assertEquals(map.connections(), loaded.connections());
    }
    @Test void restartReloadsBusinessState() throws IOException {
        Path storage = temp.resolve("state.tsv"); var first = new MapService(storage, repository);
        first.change(m -> { m.addCampus("A"); m.addCampus("B"); m.connect("A","B",7); });
        var second = new MapService(storage, repository);
        assertEquals(7, second.calculate("A","B").totalCost().orElseThrow());
    }
    @Test void invalidImportPreservesMemoryAndDisk() throws IOException {
        Path storage = temp.resolve("state.tsv"); var service = new MapService(storage, repository); service.change(m -> m.addCampus("Original"));
        Path invalid = file("MAPA_SEDES\t1\nSEDE\tA\nSEDE\tB\nCONEXION\tA\tB\t-1\n");
        assertThrows(IOException.class, () -> service.importMap(invalid));
        assertEquals(Set.of("Original"), service.snapshot().campuses()); assertEquals(Set.of("Original"), repository.load(storage).campuses());
    }
    @Test void malformedFilesAreControlledErrors() throws IOException {
        for (String text : List.of("", "MAPA_SEDES\t2", "MAPA_SEDES\t1\nUNKNOWN\tA", "MAPA_SEDES\t1\nSEDE\tA\nSEDE\tA", "MAPA_SEDES\t1\nCONEXION\tX\tY\t1", "MAPA_SEDES\t1\nSEDE\tA\nCONEXION\tA\tA\t2147483648", "MAPA_SEDES\t1\nSEDE\tA\nCONEXION\tA\tA\t1\nCONEXION\tA\tA\t2")) {
            Path path = file(text); assertThrows(IOException.class, () -> repository.load(path));
        }
    }
    @Test void saveFailureDoesNotPublishMutation() throws IOException {
        MapRepository failing = new MapRepository() {
            public CampusMap load(Path p) { return new CampusMap(); }
            public void save(Path p, CampusMap m) throws IOException { throw new IOException("Disco no disponible"); }
        };
        var service = new MapService(temp.resolve("absent.tsv"), failing);
        assertThrows(IOException.class, () -> service.change(m -> m.addCampus("A"))); assertTrue(service.snapshot().campuses().isEmpty());
    }
    @Test void snapshotCannotMutateService() throws IOException {
        var service = new MapService(temp.resolve("state.tsv"), repository); service.snapshot().addCampus("A"); assertTrue(service.snapshot().campuses().isEmpty());
    }
    @Test void importExportAndRestartRetainAcceptanceRoute() throws IOException {
        var service = new MapService(temp.resolve("state.tsv"), repository); repository.save(temp.resolve("demo.tsv"), demo());
        service.importMap(temp.resolve("demo.tsv")); service.exportMap(temp.resolve("export.tsv"));
        var restarted = new MapService(temp.resolve("export.tsv"), repository);
        assertEquals(List.of("A","C","B"), restarted.calculate("A","B").route());
    }
    @Test void emptyMapRoundTrip() throws IOException {
        Path path = temp.resolve("empty.tsv"); repository.save(path, new CampusMap()); assertTrue(repository.load(path).campuses().isEmpty());
    }
    @Test void calculationsReloadAfterRestartIncludingNoRoute() throws IOException {
        Path storage = temp.resolve("state.tsv"); var service = new MapService(storage, repository);
        repository.save(temp.resolve("demo.tsv"), demo()); service.importMap(temp.resolve("demo.tsv"));
        service.calculate("A","B"); service.calculate("A","D");
        var restarted = new MapService(storage, repository);
        assertEquals(2, restarted.history().size());
        assertEquals(List.of("A","C","B"), restarted.history().get(0).result().route());
        assertEquals(3, restarted.history().get(0).result().totalCost().orElseThrow());
        assertEquals(RouteResult.Status.SIN_RUTA, restarted.history().get(1).result().status());
    }
    @Test void invalidCalculationIsNotAddedToHistory() throws IOException {
        var service = new MapService(temp.resolve("state.tsv"), repository);
        assertThrows(IllegalArgumentException.class, () -> service.calculate("X","X")); assertTrue(service.history().isEmpty());
    }
    @Test void damagedHistoryIsControlledError() throws IOException {
        var history = new RouteHistoryRepository();
        assertThrows(IOException.class, () -> history.load(file("CALCULOS_SEDES\t1\ninvalid")));
    }
}
