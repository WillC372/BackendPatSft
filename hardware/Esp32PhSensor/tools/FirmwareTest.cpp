// Tests of the firmware logic on a computer (no ESP32 needed).
//   g++ -std=c++17 -Wall -Wextra -I tools/shim -x c++ tools/FirmwareTest.cpp -o /tmp/fw && /tmp/fw
#define USE_BLUETOOTH 0
#include "../Esp32PhSensor.ino"
#include <cstdio>
#include <string>

static int failures = 0;

static std::string ask(const std::string& command, const std::string& ending = "\n") {
  Serial.output.clear();
  Serial.send(command + ending);
  loop();
  std::string out = Serial.output;
  while (!out.empty() && (out.back() == '\n' || out.back() == '\r')) out.pop_back();
  return out;
}

static void expect(const std::string& what, const std::string& got, const std::string& want) {
  bool ok = got == want;
  if (!ok) failures++;
  std::printf("%s %s -> %s%s\n", ok ? "PASS" : "FAIL", what.c_str(), got.c_str(), ok ? "" : ("  (esperado " + want + ")").c_str());
}

static double phOf(const std::string& reply) { return std::stod(reply.substr(3, reply.find(';') - 3)); }

static void expectNear(const std::string& what, const std::string& reply, double want, double tol) {
  bool ok = reply.rfind("PH:", 0) == 0 && std::abs(phOf(reply) - want) <= tol;
  if (!ok) failures++;
  std::printf("%s %s -> %s (esperado %.2f ± %.2f)\n", ok ? "PASS" : "FAIL", what.c_str(), reply.c_str(), want, tol);
}

int main() {
  setup();
  expect("PING", ask("PING"), "PONG AQUA-ASSISTANT-PH-001");
  expect("PING con \\r\\n (Windows)", ask("PING", "\r\n"), "PONG AQUA-ASSISTANT-PH-001");
  expect("PING con \\r (Atlas)", ask("PING", "\r"), "PONG AQUA-ASSISTANT-PH-001");
  expect("comando desconocido", ask("HOLA"), "ERR:UNKNOWN_COMMAND");
  expect("linea vacia no responde", ask(""), "");

  g_millivolts = 1250;
  expect("READ sin calibrar en el punto neutro", ask("READ"), "PH:7.00;T:NA");
  g_millivolts = 1250 + 3 * 88;
  expectNear("READ sin calibrar, 3 pH mas acido", ask("READ"), 4.0, 0.01);
  g_millivolts = 0;
  expect("sensor desconectado", ask("READ"), "ERR:NO_SIGNAL");
  g_millivolts = 3300;
  expect("sin divisor (voltaje alto)", ask("READ"), "ERR:NO_SIGNAL");
  g_millivolts = 1250 + 9 * 88;
  expect("fuera de 0-14", ask("READ"), "ERR:OUT_OF_RANGE");

  // A real probe: 1300 mV in the 6.86 sachet, 1560 mV in the 4.00 sachet, 1050 mV in 9.18.
  g_millivolts = 1300;
  expect("CAL:MID:6.86", ask("CAL:MID:6.86"), "OK");
  expect("READ en el mismo sobre", ask("READ"), "PH:6.86;T:NA");
  g_millivolts = 1560;
  expect("CAL:LOW:4.00", ask("CAL:LOW:4.00"), "OK");
  expect("READ en el sobre 4.00", ask("READ"), "PH:4.00;T:NA");
  g_millivolts = 1430;
  expectNear("READ entre 4.00 y 6.86 (interpolado)", ask("READ"), 5.43, 0.01);
  g_millivolts = 1200;
  expectNear("READ sobre 7 solo con LOW (usa la pendiente de LOW)", ask("READ"), 6.86 + 100.0 / (260.0 / 2.86), 0.01);
  g_millivolts = 1050;
  expect("CAL:HIGH:9.18", ask("CAL:HIGH:9.18"), "OK");
  expect("READ en el sobre 9.18", ask("READ"), "PH:9.18;T:NA");
  g_millivolts = 1175;
  expectNear("READ entre 6.86 y 9.18", ask("READ"), 6.86 + 125.0 / (250.0 / 2.32), 0.01);

  g_millivolts = 1310;
  expect("LOW con la sonda en el sobre neutro", ask("CAL:LOW:4.00"), "ERR:SAME_AS_MID");
  expect("valor fuera de rango para MID", ask("CAL:MID:4.00"), "ERR:BAD_VALUE");
  expect("valor no numerico", ask("CAL:MID:siete"), "ERR:BAD_VALUE");
  expect("punto desconocido", ask("CAL:TOP:7.00"), "ERR:BAD_POINT");
  g_millivolts = 0;
  expect("calibrar sin sensor", ask("CAL:MID:7.00"), "ERR:NO_SIGNAL");

  // Restart: the calibration stays in flash.
  g_millivolts = 1430;
  std::string before = ask("READ");
  setup();
  expect("la calibracion sobrevive al reinicio", ask("READ"), before);
  g_millivolts = 1300;
  expect("STATUS", ask("STATUS"), "MV:1300.0;MID:1300.0@6.86;LOW:1560.0@4.00;HIGH:1050.0@9.18");
  expect("CAL:CLEAR", ask("CAL:CLEAR"), "OK");
  g_millivolts = 1250;
  expect("tras borrar vuelve a los valores de fabrica", ask("READ"), "PH:7.00;T:NA");

  // Noise: the 5 highest and 5 lowest samples are discarded.
  g_samples = {1250, 1250, 1250, 1250, 1250, 1250, 1250, 1250, 1250, 1250, 1250, 0, 3300, 0, 3300, 1250, 1250, 0, 3300, 1250, 1250};
  g_sampleIndex = 0;
  expect("picos de ruido descartados", ask("READ"), "PH:7.00;T:NA");
  g_samples.clear();

  std::string longLine(80, 'X');
  expect("linea demasiado larga se ignora", ask(longLine), "");
  expect("y la siguiente funciona", ask("PING"), "PONG AQUA-ASSISTANT-PH-001");

  std::printf("\n%s: %d fallas\n", failures ? "FALLO" : "TODO BIEN", failures);
  return failures ? 1 : 0;
}
