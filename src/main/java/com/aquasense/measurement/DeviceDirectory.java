package com.aquasense.measurement;

import java.util.Map;
import java.util.Set;

/**
 * Port the device module implements to resolve device names.
 * It keeps the measurement module from depending on the internal hardware classes.
 */
public interface DeviceDirectory {

    Map<Long, String> namesFor(Long userId, Set<Long> deviceIds);
}
