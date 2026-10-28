package com.practice.retofinal.domain;

import java.time.Instant;

/** Resultado histórico; conserva el trayecto aun cuando se edita el mapa después. */
public record RouteCalculation(Instant timestamp, String from, String to, RouteResult result) {}
