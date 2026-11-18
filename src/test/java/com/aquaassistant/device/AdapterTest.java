package com.aquaassistant.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquaassistant.device.hardware.AquaAssistantFirmwareAdapter;
import com.aquaassistant.device.hardware.AtlasEzoPhAdapter;
import com.aquaassistant.device.hardware.CalibrationPoint;
import com.aquaassistant.device.hardware.DeviceException;
import com.aquaassistant.device.hardware.PhMeter;
import com.aquaassistant.device.hardware.PhReading;
import com.aquaassistant.support.FakeSerialLink;
import org.junit.jupiter.api.Test;

class AdapterTest {

    @Test
    void atlasEzoAdapterTranslatesUartProtocol() {
        FakeSerialLink link = new FakeSerialLink().respond("*OK", "7.12", "*OK");
        PhMeter meter = new AtlasEzoPhAdapter(link);
        meter.open();
        PhReading reading = meter.read();
        assertThat(reading.ph()).isEqualTo(7.12);
        assertThat(meter.calibrate(CalibrationPoint.MID, 7.0)).contains("MID");
        assertThat(link.sent()).containsExactly("C,0", "R", "Cal,mid,7.00");
    }

    @Test
    void firmwareAdapterParsesPhAndTemperature() {
        FakeSerialLink link = new FakeSerialLink().respond("PONG AQUA-ASSISTANT-PH-001", "PH:6.85;T:24.5");
        PhMeter meter = new AquaAssistantFirmwareAdapter(link);
        meter.open();
        PhReading reading = meter.read();
        assertThat(reading.ph()).isEqualTo(6.85);
        assertThat(reading.temperatureC()).isEqualTo(24.5);
        assertThat(link.sent()).containsExactly("PING", "READ");
    }

    @Test
    void bothAdaptersExposeTheSameInterface() {
        PhMeter atlas = new AtlasEzoPhAdapter(new FakeSerialLink());
        PhMeter firmware = new AquaAssistantFirmwareAdapter(new FakeSerialLink());
        assertThat(atlas.supportsCalibration()).isTrue();
        assertThat(firmware.supportsCalibration()).isTrue();
        assertThat(atlas.isSimulated()).isFalse();
        assertThat(firmware.isSimulated()).isFalse();
    }

    @Test
    void invalidResponsesBecomeDeviceErrors() {
        PhMeter meter = new AquaAssistantFirmwareAdapter(new FakeSerialLink().respond("HELLO"));
        assertThatThrownBy(meter::open).isInstanceOf(DeviceException.class);
        PhMeter atlas = new AtlasEzoPhAdapter(new FakeSerialLink().respond("abc"));
        assertThatThrownBy(atlas::read).isInstanceOf(DeviceException.class);
    }
}
