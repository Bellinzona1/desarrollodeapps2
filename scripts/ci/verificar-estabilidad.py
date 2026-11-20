"""Exige tres inventarios JUnit iguales, sin fallas ni omisiones, incluidos los IT."""

import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


def inventario(carpeta):
    casos = Counter()
    totales = {}
    for nivel in ("surefire-reports", "failsafe-reports"):
        archivos = sorted((Path(carpeta) / nivel).glob("TEST-*.xml"))
        if not archivos:
            raise ValueError(f"{carpeta}: faltan informes de {nivel}")
        cantidad = 0
        for archivo in archivos:
            raiz = ET.parse(archivo).getroot()
            for caso in raiz.iter("testcase"):
                nombre = (nivel, caso.get("classname", ""), caso.get("name", ""))
                if any(caso.find(estado) is not None for estado in ("failure", "error", "skipped")):
                    raise ValueError(f"{carpeta}: caso fallado u omitido: {nombre}")
                casos[nombre] += 1
                cantidad += 1
        if cantidad == 0:
            raise ValueError(f"{carpeta}: {nivel} no contiene casos ejecutados")
        totales[nivel] = cantidad
    return casos, totales


def main():
    if len(sys.argv) != 4:
        raise ValueError("Uso: verificar-estabilidad.py corrida1 corrida2 corrida3")
    referencia = None
    resumen = []
    for carpeta in sys.argv[1:]:
        casos, totales = inventario(carpeta)
        if referencia is not None and casos != referencia:
            raise ValueError(f"{carpeta}: cambio el inventario de pruebas entre corridas")
        referencia = casos
        resumen.append({"corrida": carpeta, "pasados": totales, "fallados": 0, "omitidos": 0})
    print(json.dumps({"estable": True, "corridas": resumen}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(str(error))
