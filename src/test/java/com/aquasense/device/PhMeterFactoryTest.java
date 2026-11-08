package com.aquasense.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.device.hardware.AquaSenseFirmwareAdapter;
import com.aquasense.device.hardware.AtlasEzoPhAdapter;
import com.aquasense.device.hardware.DeviceException;
import com.aquasense.device.hardware.MeterProtocol;
import com.aquasense.device.hardware.PhMeterFactory;
import com.aquasense.device.hardware.SimulatedPhMeter;
import org.junit.jupiter.api.Test;

class PhMeterFactoryTest {

    private final PhMeterFactory factory = new PhMeterFactory();

    @Test
    void createsAdapterForEachProtocol() {
        assertThat(factory.create(MeterProtocol.SIMULATOR, null)).isInstanceOf(SimulatedPhMeter.class);
        assertThat(factory.create(MeterProtocol.ATLAS_EZO, "/dev/ttyUSB0")).isInstanceOf(AtlasEzoPhAdapter.class);
        assertThat(factory.create(MeterProtocol.AQUASENSE_ESP32, "COM5")).isInstanceOf(AquaSenseFirmwareAdapter.class);
    }

    @Test
    void simulatorIsAlwaysMarkedAsSimulated() {
        var meter = factory.create(MeterProtocol.SIMULATOR, null);
        meter.open();
        assertThat(meter.isSimulated()).isTrue();
        assertThat(meter.read().ph()).isBetween(0.0, 14.0);
    }

    @Test
    void realProtocolsRequireAPort() {
        assertThatThrownBy(() -> factory.create(MeterProtocol.ATLAS_EZO, " ")).isInstanceOf(DeviceException.class);
    }
}
