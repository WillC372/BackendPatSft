// Minimal stand-in for the Arduino core so the firmware logic can be compiled and tested on a computer.
#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <deque>
#include <string>
#include <vector>

#define ADC_11db 3
inline float g_millivolts = 0;           // voltage that the test puts on GPIO34
inline std::vector<float> g_samples;     // optional: one value per sample (overrides g_millivolts)
inline size_t g_sampleIndex = 0;
inline uint32_t analogReadMilliVolts(int) {
  if (!g_samples.empty()) return (uint32_t) g_samples[g_sampleIndex++ % g_samples.size()];
  return (uint32_t) g_millivolts;
}
inline void analogReadResolution(int) {}
inline void analogSetPinAttenuation(int, int) {}
inline void delay(unsigned long) {}

class Stream {
 public:
  std::deque<char> input;
  std::string output;
  void begin(unsigned long) {}
  int available() { return (int) input.size(); }
  int read() { char c = input.front(); input.pop_front(); return c; }
  void println(const char* s) { output += s; output += "\r\n"; }
  void send(const std::string& s) { for (char c : s) input.push_back(c); }
};
inline Stream Serial;
