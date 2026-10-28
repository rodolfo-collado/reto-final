package com.practice.retofinal.domain;

import java.util.List;
import java.util.OptionalLong;

public record RouteResult(Status status, List<String> route, OptionalLong totalCost) {
    public enum Status { ENCONTRADA, SIN_RUTA }
    public RouteResult { route = List.copyOf(route); }
    public static RouteResult missing() { return new RouteResult(Status.SIN_RUTA, List.of(), OptionalLong.empty()); }
    public static RouteResult found(List<String> route, long cost) {
        return new RouteResult(Status.ENCONTRADA, route, OptionalLong.of(cost));
    }
}
