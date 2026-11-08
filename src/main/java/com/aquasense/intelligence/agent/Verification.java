package com.aquasense.intelligence.agent;

import java.util.List;

/**
 * Resultado de contrastar las cifras de pH del informe con los datos que devolvieron las herramientas.
 */
public record Verification(int checked, int verified, List<String> unverified) {
}
