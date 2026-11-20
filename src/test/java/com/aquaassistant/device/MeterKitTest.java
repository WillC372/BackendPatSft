package com.aquaassistant.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquaassistant.device.hardware.AquaAssistantFirmwareAdapter;
import com.aquaassistant.device.hardware.AtlasEzoKit;
import com.aquaassistant.device.hardware.AtlasEzoPhAdapter;
import com.aquaassistant.device.hardware.DeviceException;
import com.aquaassistant.device.hardware.Esp32FirmwareKit;
import com.aquaassistant.device.hardware.JSerialCommLink;
import com.aquaassistant.device.hardware.MeterKit;
import com.aquaassistant.device.hardware.MeterProtocol;
import com.aquaassistant.device.hardware.PhMeter;
import com.aquaassistant.device.hardware.PhMeterFactory;
import com.aquaassistant.device.hardware.TextMeterAdapter;
import com.aquaassistant.device.hardware.TextMeterKit;
import com.aquaassistant.support.FakeSerialLink;
import org.junit.jupiter.api.Test;

/** Abstract Factory: each meter family creates the link and the adapter that belong together. */
class MeterKitTest {

    @Test
    void eachFamilyCreatesItsOwnLinkAndAdapter() {
        assertFamily(new AtlasEzoKit(), MeterProtocol.ATLAS_EZO, "\r", AtlasEzoPhAdapter.class);
        assertFamily(new Esp32FirmwareKit(), MeterProtocol.AQUASENSE_ESP32, "\n", AquaAssistantFirmwareAdapter.class);
        assertFamily(new TextMeterKit(), MeterProtocol.TEXT_METER, "\r\n", TextMeterAdapter.class);
    }

    @Test
    void theFactoryBuildsEveryRealProtocolWithItsFamily() {
        PhMeterFactory factory = new PhMeterFactory();
        for (MeterProtocol protocol : MeterProtocol.values()) {
            if (protocol == MeterProtocol.SIMULATOR) {
                continue;
            }
            assertThat(factory.kitFor(protocol).protocol()).isEqualTo(protocol);
            assertThat(factory.create(protocol, "COM7").protocol()).isEqualTo(protocol);
        }
        assertThat(factory.create(MeterProtocol.TEXT_METER, "COM7")).isInstanceOf(TextMeterAdapter.class);
    }

    @Test
    void aFamilyNeedsARealPort() {
        assertThatThrownBy(() -> new Esp32FirmwareKit().createLink(" ")).isInstanceOf(DeviceException.class);
        assertThatThrownBy(() -> new AtlasEzoKit().createLink(null)).isInstanceOf(DeviceException.class);
    }

    @Test
    void theAdapterOfAFamilySpeaksItsProtocolOverTheLink() {
        FakeSerialLink link = new FakeSerialLink().respond("PONG AQUA-ASSISTANT-PH-001", "PH:7.05;T:23.0");
        PhMeter meter = new Esp32FirmwareKit().createMeter(link);
        meter.open();
        assertThat(meter.read().ph()).isEqualTo(7.05);
        assertThat(link.sent()).contains("READ");
    }

    private static void assertFamily(MeterKit kit, MeterProtocol protocol, String terminator, Class<?> adapter) {
        assertThat(kit.protocol()).isEqualTo(protocol);
        assertThat(kit.createLink("/dev/ttyUSB0")).isInstanceOf(JSerialCommLink.class)
            .extracting(link -> ((JSerialCommLink) link).terminator()).isEqualTo(terminator);
        assertThat(kit.createMeter(new FakeSerialLink())).isInstanceOf(adapter);
    }
}
