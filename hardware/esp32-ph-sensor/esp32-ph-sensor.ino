// AquaSense firmware for ESP32 + analog pH probe (for example, DFRobot Gravity pH V2).
// It exposes the text protocol understood by AquaSenseFirmwareAdapter over USB (Serial) and over
// classic Bluetooth SPP (BluetoothSerial). NOT compiled or tested with hardware in this delivery.
//
// Protocol (lines terminated with \n):
//   PING            -> PONG AQUASENSE-PH-001
//   READ            -> PH:7.12;T:NA
//   CAL:MID:7.00    -> OK   (also CAL:LOW:4.00 and CAL:HIGH:10.00)

#include <BluetoothSerial.h>
#include <Preferences.h>

const int PH_PIN = 34;
const int SAMPLES = 20;
const char* DEVICE_ID = "AQUASENSE-PH-001";

BluetoothSerial bt;
Preferences prefs;
float neutralVoltage = 1500.0;  // mV at pH 7 (replaced on calibration)
float acidVoltage = 2032.0;     // mV at pH 4 (replaced on calibration)

float readMillivolts() {
  long total = 0;
  for (int i = 0; i < SAMPLES; i++) {
    total += analogReadMilliVolts(PH_PIN);
    delay(10);
  }
  return total / (float) SAMPLES;
}

float voltageToPh(float mv) {
  float slope = (7.0 - 4.0) / (neutralVoltage - acidVoltage);
  return 7.0 + slope * (mv - neutralVoltage);
}

String handle(String line) {
  line.trim();
  if (line == "PING") return String("PONG ") + DEVICE_ID;
  if (line == "READ") {
    float ph = voltageToPh(readMillivolts());
    if (ph < 0 || ph > 14) return "ERR:OUT_OF_RANGE";
    return "PH:" + String(ph, 2) + ";T:NA";
  }
  if (line.startsWith("CAL:")) {
    float mv = readMillivolts();
    if (line.startsWith("CAL:MID:")) { neutralVoltage = mv; prefs.putFloat("neutral", mv); return "OK"; }
    if (line.startsWith("CAL:LOW:")) { acidVoltage = mv; prefs.putFloat("acid", mv); return "OK"; }
    if (line.startsWith("CAL:HIGH:")) return "ERR:HIGH_NOT_SUPPORTED";
    return "ERR:BAD_POINT";
  }
  return "ERR:UNKNOWN_COMMAND";
}

void setup() {
  Serial.begin(9600);
  bt.begin(DEVICE_ID);
  prefs.begin("aquasense", false);
  neutralVoltage = prefs.getFloat("neutral", neutralVoltage);
  acidVoltage = prefs.getFloat("acid", acidVoltage);
  analogReadResolution(12);
}

void loop() {
  if (Serial.available()) Serial.println(handle(Serial.readStringUntil('\n')));
  if (bt.available()) bt.println(handle(bt.readStringUntil('\n')));
}
