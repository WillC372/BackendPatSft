package com.aquaassistant.device.hardware;

/** Family of the project's ESP32 firmware (ESP32 + PH-4502C): commands end with a new line ("READ\n"). */
public class Esp32FirmwareKit extends SerialMeterKit {

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.AQUASENSE_ESP32;
    }

    @Override
    protected String terminator() {
        return "\n";
    }

    @Override
    public PhMeter createMeter(SerialLink link) {
        return new AquaAssistantFirmwareAdapter(link);
    }
}
