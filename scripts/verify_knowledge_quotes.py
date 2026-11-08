"""Comprueba que cada cita de knowledge/guidelines.json aparezca literal en los documentos oficiales descargados.

Uso: python3 -I scripts/verify_knowledge_quotes.py src/main/resources/knowledge/guidelines.json DIRECTORIO
El directorio debe contener el texto de cada fuente (ver docs/RAG.md): epa.txt, who-ph.txt, mahc-code.txt,
fao-01.txt y fao-06.txt. Solo se normalizan espacios, guiones de corte de línea y comillas tipográficas.
Las citas con "[...]" se verifican por partes.
"""
import json
import re
import sys

SOURCES = {
    "epa-sdws": ["epa.txt"],
    "who-gdwq-ph": ["who-ph.txt"],
    "cdc-mahc-2024": ["mahc-code.txt"],
    "fao-ipd29": ["fao-01.txt", "fao-06.txt"],
}


def normalize(text):
    text = re.sub("­\\s*", "", text).replace("’", "'").replace("–", "-")
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r" ([,.;:)])", r"\1", text)
    return text.replace("( ", "(").lower()


def main(kb_path, folder):
    kb = json.load(open(kb_path, encoding="utf-8"))
    texts = {sid: normalize(" ".join(open(f"{folder}/{f}", encoding="utf-8", errors="ignore").read() for f in files))
             for sid, files in SOURCES.items()}
    missing = 0
    for chunk in kb["chunks"]:
        for part in (p.strip() for p in chunk["quote"].split("[...]")):
            if normalize(part) not in texts[chunk["sourceId"]]:
                missing += 1
                print("NO ENCONTRADA:", chunk["id"], "::", part[:90])
    print(f"{len(kb['chunks'])} fragmentos, {missing} partes sin encontrar")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
