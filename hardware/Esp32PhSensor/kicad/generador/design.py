"""Single source of the circuit: components, pins and nets used by the schematic, the PCB and the breadboard view."""

PROJECT = "AquaAssistantPh"
TITLE = "Aqua Assistant AI - Medidor de pH (ESP32 + PH-4502C)"
REV = "1.0"
DATE = "2026-11-16"
COMPANY = "Aqua Assistant AI"

# ESP32 DevKit 38 pines (USB-C), rotulos tal como estan impresos en la placa del usuario.
ESP_LEFT = ["3V3", "EN", "VP", "VN", "G34", "G35", "G32", "G33", "G25", "G26", "G27", "G14", "G12", "GND", "G13", "SD2", "SD3", "CMD", "5V"]
ESP_RIGHT = ["GND", "G23", "G22", "TXD", "RXD", "G21", "GND", "G19", "G18", "G5", "G17", "G16", "G4", "G0", "G2", "G15", "SD1", "SD0", "CLK"]
ESP_PIN = {}  # name -> pad number (first occurrence for GND is pin 14)
for i, n in enumerate(ESP_LEFT):
    ESP_PIN.setdefault(n, str(i + 1))
for i, n in enumerate(ESP_RIGHT):
    ESP_PIN.setdefault(n + "_R" if n == "GND" else n, str(i + 20))

# PH-4502C header: To Do Po G G V+ (pins 1..6).
PH_PINS = {"To": "1", "Do": "2", "Po": "3", "G": "4", "G2": "5", "V+": "6"}

# Board components: ref -> (library footprint, value, description)
PARTS = {
    "U1": ("AquaAssistant:ESP32_DevKit_38P_Socket", "ESP32_DevKit_38P", "ESP32 DevKit 38 pines USB-C en 2 zocalos hembra 1x19 (25,4 mm entre filas)"),
    "R1": ("Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P10.16mm_Horizontal", "20k", "Resistencia 20 kOhm 1/4 W 1 %"),
    "R2": ("Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P10.16mm_Horizontal", "20k", "Resistencia 20 kOhm 1/4 W 1 %"),
    "J1": ("Connector_PinSocket_2.54mm:PinSocket_1x03_P2.54mm_Vertical", "SENSOR", "Zocalo hembra 1x3 paso 2,54 mm (cable Dupont al PH-4502C)"),
    "H1": ("MountingHole:MountingHole_3.2mm_M3", "M3", "Agujero de montaje M3"),
    "H2": ("MountingHole:MountingHole_3.2mm_M3", "M3", "Agujero de montaje M3"),
    "H3": ("MountingHole:MountingHole_3.2mm_M3", "M3", "Agujero de montaje M3"),
    "H4": ("MountingHole:MountingHole_3.2mm_M3", "M3", "Agujero de montaje M3"),
}
# Off-board module (schematic and breadboard only).
OFFBOARD = {"U2": ("", "PH-4502C", "Modulo amplificador de pH PH-4502C con sonda E201-BNC (fuera de la placa)")}

# Nets: name -> list of (ref, pin). The same list is checked against the schematic netlist and the PCB.
NETS = {
    "/PH_OUT": [("J1", "1"), ("R1", "1"), ("U2", PH_PINS["Po"])],
    "/PH_ADC": [("R1", "2"), ("R2", "1"), ("U1", ESP_PIN["G34"])],
    "GND": [("J1", "2"), ("R2", "2"), ("U1", ESP_PIN["GND"]), ("U2", PH_PINS["G"])],
    "+5V": [("J1", "3"), ("U1", ESP_PIN["5V"]), ("U2", PH_PINS["V+"])],
}
