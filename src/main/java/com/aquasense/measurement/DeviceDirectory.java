package com.aquasense.measurement;

import java.util.Map;
import java.util.Set;

/**
 * Puerto que el modulo de dispositivos implementa para resolver nombres de dispositivos.
 * Evita que el modulo de mediciones dependa de las clases internas de hardware.
 */
public interface DeviceDirectory {

    Map<Long, String> namesFor(Long userId, Set<Long> deviceIds);
}
