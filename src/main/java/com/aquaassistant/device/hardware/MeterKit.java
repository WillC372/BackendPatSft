package com.aquaassistant.device.hardware;

/**
 * Abstract Factory: each meter model is a family of objects that only work together, the serial link set up the way
 * that model expects (its line terminator) and the adapter that speaks its protocol. A kit creates both, so the link
 * of one model is never combined with the adapter of another. Adding a model means adding one kit.
 */
public interface MeterKit {

    /** The model this family belongs to. */
    MeterProtocol protocol();

    /** First product: the serial link configured for this model. */
    SerialLink createLink(String port);

    /** Second product: the adapter of this model, over a link of the same family. */
    PhMeter createMeter(SerialLink link);
}
