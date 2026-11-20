package com.aquaassistant.device.hardware;

/** Family of the Atlas Scientific EZO-pH circuit: commands end with a carriage return ("R\r"). */
public class AtlasEzoKit extends SerialMeterKit {

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.ATLAS_EZO;
    }

    @Override
    protected String terminator() {
        return "\r";
    }

    @Override
    public PhMeter createMeter(SerialLink link) {
        return new AtlasEzoPhAdapter(link);
    }
}
