package com.aquaassistant.device.hardware;

/** Family of laboratory meters that print their readings as text: lines end with CR LF. */
public class TextMeterKit extends SerialMeterKit {

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.TEXT_METER;
    }

    @Override
    protected String terminator() {
        return "\r\n";
    }

    @Override
    public PhMeter createMeter(SerialLink link) {
        return new TextMeterAdapter(link);
    }
}
