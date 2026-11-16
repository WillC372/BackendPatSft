// Copia del firmware para la simulación en Wokwi (sin Bluetooth, que Wokwi no simula).
// Generada desde ../Esp32PhSensor.ino: si cambias el firmware, vuelve a copiarlo y deja esta línea:
#define USE_BLUETOOTH 0

// AquaSense firmware for an ESP32 (38-pin DevKit, ESP-32 module) + PH-4502C pH module.
//
// Wiring (see planos/): PH-4502C V+ -> 5V, G -> GND, Po -> R1 20k -> GPIO34, and R2 20k from GPIO34 to GND.
// The divider halves Po (0-5 V) so GPIO34 never receives more than 2.5 V.
//
// Text protocol, one command per line (\n, \r or \r\n), 9600 baud over USB and over classic Bluetooth (SPP):
//   PING              -> PONG AQUASENSE-PH-001
//   READ              -> PH:7.02;T:NA       (ERR:NO_SIGNAL if the sensor is not connected, ERR:OUT_OF_RANGE)
//   CAL:MID:6.86      -> OK                 (buffer between 6.00 and 8.00; also CAL:LOW:4.00 and CAL:HIGH:9.18)
//   CAL:CLEAR         -> OK                 (back to the uncalibrated defaults)
//   STATUS            -> MV:1251.3;MID:1250.0@7.00;LOW:-;HIGH:-
// The calibration uses the value written in each command, so 6.86 and 9.18 sachets work as well as 7.00 and 10.00.
// Compiled and tested on a computer with a simulated Arduino layer (tools/FirmwareTest.cpp); not yet on hardware.

#ifndef USE_BLUETOOTH
#define USE_BLUETOOTH 1  // 0 in the Wokwi simulation, which has no Bluetooth
#endif

#include <Arduino.h>
#include <Preferences.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if USE_BLUETOOTH
#include <BluetoothSerial.h>
#endif

const int PH_PIN = 34;            // printed "G34" on the board; ADC1, works with Bluetooth on
const int SAMPLES = 21;           // readings per measurement; the 5 lowest and 5 highest are discarded
const char* DEVICE_ID = "AQUASENSE-PH-001";

// Before calibrating: about 2.5 V from the PH-4502C at pH 7 (1250 mV after the divider) and about -88 mV per pH unit.
// These are approximations; every probe is different, so the meter must be calibrated with buffer solutions.
const float DEFAULT_MID_MV = 1250.0f;
const float DEFAULT_MID_PH = 7.0f;
const float DEFAULT_SLOPE = -88.0f;  // mV per pH unit at GPIO34
const float MIN_SIGNAL_MV = 30.0f;   // below this nothing is connected (or Po is shorted to ground)
const float MAX_SIGNAL_MV = 3000.0f; // above this the divider is missing or Po is wrong
const float MIN_POINT_GAP_MV = 25.0f;

enum { LOW_POINT = 0, MID_POINT = 1, HIGH_POINT = 2 };
const char* POINT_KEYS[3] = {"low", "mid", "high"};

struct CalPoint {
  float mv;
  float ph;
  bool set;
};

CalPoint points[3];
Preferences prefs;
#if USE_BLUETOOTH
BluetoothSerial bt;
#endif

// ---------- measurement ----------

static int compareFloats(const void* a, const void* b) {
  float x = *(const float*) a, y = *(const float*) b;
  return (x > y) - (x < y);
}

float readMillivolts() {
  float values[SAMPLES];
  for (int i = 0; i < SAMPLES; i++) {
    values[i] = (float) analogReadMilliVolts(PH_PIN);
    delay(10);
  }
  qsort(values, SAMPLES, sizeof(float), compareFloats);
  float total = 0;
  for (int i = 5; i < SAMPLES - 5; i++) total += values[i];
  return total / (SAMPLES - 10);
}

CalPoint midPoint() {
  if (points[MID_POINT].set) return points[MID_POINT];
  CalPoint p = {DEFAULT_MID_MV, DEFAULT_MID_PH, true};
  return p;
}

// mV per pH unit for this reading: the calibrated point on the same side of the middle, else the other one, else the default.
float slopeFor(float mv, const CalPoint& mid) {
  // The PH-4502C gives more voltage for more acidic water, so voltages above the middle are on the LOW (acid) side.
  int same = mv >= mid.mv ? LOW_POINT : HIGH_POINT;
  int other = same == LOW_POINT ? HIGH_POINT : LOW_POINT;
  for (int idx : {same, other}) {
    const CalPoint& p = points[idx];
    if (p.set && fabsf(p.ph - mid.ph) > 0.5f) return (p.mv - mid.mv) / (p.ph - mid.ph);
  }
  return DEFAULT_SLOPE;
}

float phFromMillivolts(float mv) {
  CalPoint mid = midPoint();
  return mid.ph + (mv - mid.mv) / slopeFor(mv, mid);
}

// ---------- calibration storage ----------

void loadCalibration() {
  for (int i = 0; i < 3; i++) {
    char key[12];
    snprintf(key, sizeof key, "%s_mv", POINT_KEYS[i]);
    float mv = prefs.getFloat(key, NAN);
    snprintf(key, sizeof key, "%s_ph", POINT_KEYS[i]);
    float ph = prefs.getFloat(key, NAN);
    points[i].set = !isnan(mv) && !isnan(ph);
    points[i].mv = mv;
    points[i].ph = ph;
  }
}

void saveCalibration(int idx) {
  char key[12];
  snprintf(key, sizeof key, "%s_mv", POINT_KEYS[idx]);
  prefs.putFloat(key, points[idx].mv);
  snprintf(key, sizeof key, "%s_ph", POINT_KEYS[idx]);
  prefs.putFloat(key, points[idx].ph);
}

void clearCalibration() {
  prefs.clear();
  for (int i = 0; i < 3; i++) points[i].set = false;
}

// ---------- commands ----------

bool parseNumber(const char* text, float* out) {
  if (!text || !*text) return false;
  char* end;
  float v = strtof(text, &end);
  if (end == text || *end != '\0' || isnan(v)) return false;
  *out = v;
  return true;
}

void calibrate(const char* args, char* reply, size_t size) {
  if (strcmp(args, "CLEAR") == 0) {
    clearCalibration();
    snprintf(reply, size, "OK");
    return;
  }
  int idx;
  float minPh, maxPh;
  const char* value;
  if (strncmp(args, "MID:", 4) == 0) { idx = MID_POINT; minPh = 6.0f; maxPh = 8.0f; value = args + 4; }
  else if (strncmp(args, "LOW:", 4) == 0) { idx = LOW_POINT; minPh = 2.0f; maxPh = 5.5f; value = args + 4; }
  else if (strncmp(args, "HIGH:", 5) == 0) { idx = HIGH_POINT; minPh = 8.5f; maxPh = 12.0f; value = args + 5; }
  else { snprintf(reply, size, "ERR:BAD_POINT"); return; }

  float ph;
  if (!parseNumber(value, &ph) || ph < minPh || ph > maxPh) { snprintf(reply, size, "ERR:BAD_VALUE"); return; }
  float mv = readMillivolts();
  if (mv < MIN_SIGNAL_MV || mv > MAX_SIGNAL_MV) { snprintf(reply, size, "ERR:NO_SIGNAL"); return; }
  if (idx != MID_POINT) {
    // The probe must read differently in this buffer than in the middle one (otherwise it is in the wrong jar).
    CalPoint mid = midPoint();
    if (fabsf(mv - mid.mv) < MIN_POINT_GAP_MV) { snprintf(reply, size, "ERR:SAME_AS_MID"); return; }
  }
  points[idx].mv = mv;
  points[idx].ph = ph;
  points[idx].set = true;
  saveCalibration(idx);
  snprintf(reply, size, "OK");
}

void describePoint(int idx, char* out, size_t size) {
  if (points[idx].set) snprintf(out, size, "%.1f@%.2f", points[idx].mv, points[idx].ph);
  else snprintf(out, size, "-");
}

// Handles one command line and writes the answer in reply.
void handle(char* line, char* reply, size_t size) {
  // Trim spaces at both ends.
  while (*line == ' ') line++;
  size_t n = strlen(line);
  while (n > 0 && line[n - 1] == ' ') line[--n] = '\0';

  if (strcmp(line, "PING") == 0) {
    snprintf(reply, size, "PONG %s", DEVICE_ID);
  } else if (strcmp(line, "READ") == 0) {
    float mv = readMillivolts();
    if (mv < MIN_SIGNAL_MV || mv > MAX_SIGNAL_MV) { snprintf(reply, size, "ERR:NO_SIGNAL"); return; }
    float ph = phFromMillivolts(mv);
    if (ph < 0.0f || ph > 14.0f) { snprintf(reply, size, "ERR:OUT_OF_RANGE"); return; }
    snprintf(reply, size, "PH:%.2f;T:NA", ph);
  } else if (strncmp(line, "CAL:", 4) == 0) {
    calibrate(line + 4, reply, size);
  } else if (strcmp(line, "STATUS") == 0) {
    char low[24], mid[24], high[24];
    describePoint(LOW_POINT, low, sizeof low);
    describePoint(MID_POINT, mid, sizeof mid);
    describePoint(HIGH_POINT, high, sizeof high);
    snprintf(reply, size, "MV:%.1f;MID:%s;LOW:%s;HIGH:%s", readMillivolts(), mid, low, high);
  } else {
    snprintf(reply, size, "ERR:UNKNOWN_COMMAND");
  }
}

// Collects characters until the end of a line; works with \n, \r or \r\n and ignores lines that are too long.
struct LineReader {
  char buffer[48];
  size_t length = 0;
  bool overflow = false;

  bool feed(char c, char* out, size_t size) {
    if (c == '\n' || c == '\r') {
      bool ready = length > 0 && !overflow;
      if (ready) {
        buffer[length] = '\0';
        strncpy(out, buffer, size - 1);
        out[size - 1] = '\0';
      }
      length = 0;
      overflow = false;
      return ready;
    }
    if (length < sizeof buffer - 1) buffer[length++] = c;
    else overflow = true;
    return false;
  }
};

LineReader usbLine;
#if USE_BLUETOOTH
LineReader btLine;
#endif

void serve(Stream& stream, LineReader& reader) {
  while (stream.available()) {
    char line[48], reply[96];
    if (reader.feed((char) stream.read(), line, sizeof line)) {
      handle(line, reply, sizeof reply);
      stream.println(reply);
    }
  }
}

void setup() {
  Serial.begin(9600);
  analogReadResolution(12);
  analogSetPinAttenuation(PH_PIN, ADC_11db);  // measures up to about 3.1 V
  prefs.begin("aquasense", false);
  loadCalibration();
#if USE_BLUETOOTH
  bt.begin(DEVICE_ID);
#endif
}

void loop() {
  serve(Serial, usbLine);
#if USE_BLUETOOTH
  serve(bt, btLine);
#endif
  delay(2);
}
