// Stand-in for the ESP32 Preferences (NVS) library: values survive a simulated restart.
#pragma once
#include <cmath>
#include <map>
#include <string>

inline std::map<std::string, float> g_nvs;
class Preferences {
 public:
  bool begin(const char*, bool) { return true; }
  float getFloat(const char* key, float fallback) { auto it = g_nvs.find(key); return it == g_nvs.end() ? fallback : it->second; }
  size_t putFloat(const char* key, float v) { g_nvs[key] = v; return 4; }
  bool clear() { g_nvs.clear(); return true; }
};
