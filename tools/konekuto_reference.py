#!/usr/bin/env python3
"""Independent, exact-arithmetic oracle for the bundled score. No WindScore code is imported.

Measure origins use maximum content extent; ties form an explicit interval graph.
This oracle intentionally supports the features present in this fixture, not all MusicXML.
"""
import argparse
import hashlib
import json
from collections import defaultdict
from fractions import Fraction
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "composeApp/src/desktopMain/resources/konekuto-claris.mxl"
DEFAULT_OUTPUT = ROOT / "composeApp/src/desktopTest/resources/konekuto-reference.json"


def reference(source):
    with zipfile.ZipFile(source) as archive:
        container = ET.fromstring(archive.read("META-INF/container.xml"))
        rootfile = next(e for e in container.iter() if e.tag.rsplit("}", 1)[-1] == "rootfile")
        xml = archive.read(rootfile.attrib["full-path"])
    document = ET.fromstring(xml)
    assert document.tag == "score-partwise"
    segments, tempos, measures = [], [], []
    pitched_index = 0
    total = Fraction(0)
    steps = dict(C=0, D=2, E=4, F=5, G=7, A=9, B=11)
    for part in document.findall("part"):
        origin, divisions = Fraction(0), 4
        for measure in part.findall("measure"):
            cursor, extent, chord_onset = Fraction(0), Fraction(0), Fraction(0)
            for node in measure:
                if node.tag == "attributes":
                    divisions = int(node.findtext("divisions", divisions))
                    assert divisions > 0
                elif node.tag in ("backup", "forward"):
                    duration = Fraction(node.findtext("duration")) / divisions
                    cursor += duration if node.tag == "forward" else -duration
                    assert cursor >= 0
                    extent = max(extent, cursor)
                elif node.tag == "note":
                    assert node.find("grace") is None and node.find("cue") is None
                    duration = Fraction(node.findtext("duration", "0")) / divisions
                    chord = node.find("chord") is not None
                    onset = chord_onset if chord else cursor
                    if not chord:
                        chord_onset = cursor
                        cursor += duration
                    extent = max(extent, onset + duration)
                    pitch = node.find("pitch")
                    if pitch is not None:
                        alter = Fraction(pitch.findtext("alter", "0"))
                        assert alter.denominator == 1
                        midi = (int(pitch.findtext("octave")) + 1) * 12 + steps[pitch.findtext("step")] + int(alter)
                        segments.append(dict(source=pitched_index, part=part.attrib["id"],
                            measure=measure.attrib["number"], voice=node.findtext("voice", ""),
                            staff=int(node.findtext("staff", "1")), pitch=midi,
                            onset=origin + onset, duration=duration,
                            ties={t.attrib["type"] for t in node.findall("tie")}))
                        pitched_index += 1
                elif node.tag in ("direction", "sound"):
                    sounds = [node] if node.tag == "sound" else node.findall("sound")
                    for sound in sounds:
                        if "tempo" not in sound.attrib:
                            continue
                        offset = sound.find("offset")
                        if offset is None:
                            candidate = node.find("offset")
                            if candidate is not None and candidate.get("sound") == "yes":
                                offset = candidate
                        delta = Fraction(offset.text) / divisions if offset is not None else Fraction(0)
                        tempos.append((origin + cursor + delta, Fraction(sound.attrib["tempo"]), measure.attrib["number"]))
                elif node.tag == "barline":
                    assert node.find("repeat") is None and node.find("ending") is None
            measures.append(dict(part=part.attrib["id"], number=measure.attrib["number"], onset=origin, duration=extent))
            origin += extent
        total = max(total, origin)

    # Resolve the actual source's ties by unique temporal endpoints, independent of parser allocation order.
    stops = defaultdict(list)
    for i, note in enumerate(segments):
        if "stop" in note["ties"]:
            stops[(note["part"], note["voice"], note["pitch"], note["onset"])].append(i)
    successors, incoming = {}, set()
    for i, note in enumerate(segments):
        if "start" not in note["ties"]:
            continue
        candidates = stops[(note["part"], note["voice"], note["pitch"], note["onset"] + note["duration"])]
        assert len(candidates) == 1, (i, candidates)
        successor = candidates[0]
        assert successor not in incoming
        successors[i] = successor
        incoming.add(successor)
    events = []
    for i, note in enumerate(segments):
        if i in incoming:
            continue
        chain, current = [], i
        while True:
            chain.append(current)
            if current not in successors:
                break
            current = successors[current]
        duration = sum((segments[j]["duration"] for j in chain), Fraction(0))
        events.append(dict(pitch=note["pitch"], startTick=note["onset"] * 4,
            durationTick=duration * 4, staff=note["staff"], voice=note["voice"],
            measure=note["measure"], sourceNotes=chain))
    events.sort(key=lambda e: (e["startTick"], e["pitch"], e["sourceNotes"][0]))
    tempos.sort(key=lambda entry: entry[0])
    assert len({tick for tick, _, _ in tempos}) == len(tempos)

    def milliseconds_at(quarter):
        elapsed, position, bpm = Fraction(0), Fraction(0), Fraction(120)
        for tick, tempo, _ in tempos:
            if tick > quarter:
                break
            elapsed += (tick - position) * 60000 / bpm
            position, bpm = tick, tempo
        return elapsed + (quarter - position) * 60000 / bpm

    def integer(value):
        assert value.denominator == 1
        return int(value)

    for event in events:
        event["startTick"] = integer(event["startTick"])
        event["durationTick"] = integer(event["durationTick"])
        event["onsetMilliseconds"] = float(milliseconds_at(Fraction(event["startTick"], 4)))
        event["releaseMilliseconds"] = float(milliseconds_at(Fraction(event["startTick"] + event["durationTick"], 4)))
    return dict(sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        xmlSha256=hashlib.sha256(xml).hexdigest(), divisions=4,
        measureCount=len(measures), pitchedSegments=len(segments), tieEdges=len(successors),
        eventCount=len(events), groupCount=len({e["startTick"] for e in events}),
        totalTicks=integer(total * 4), totalMilliseconds=float(milliseconds_at(total)),
        tempoChanges=[dict(tick=integer(t * 4), bpm=float(b), measure=m,
            milliseconds=float(milliseconds_at(t))) for t, b, m in tempos],
        measures=[dict(number=m["number"], startTick=integer(m["onset"] * 4),
            durationTick=integer(m["duration"] * 4)) for m in measures], events=events,
        writtenNotes=[dict(pitch=n["pitch"], startTick=integer(n["onset"] * 4),
            durationTick=integer(n["duration"] * 4), staff=n["staff"], voice=n["voice"],
            measure=n["measure"], source=n["source"]) for n in segments])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = reference(args.source)
    encoded = json.dumps(data, indent=2, sort_keys=True) + "\n"
    if args.check:
        assert args.output.read_text() == encoded, "Reference fixture is stale"
    else:
        args.output.write_text(encoded)
    print(json.dumps({k: v for k, v in data.items() if k not in ("events", "measures", "writtenNotes")}, indent=2))
    print("measure 165:", next(m for m in data["measures"] if m["number"] == "165"))
    print("final events:", [e for e in data["events"] if e["measure"] == "165"])


if __name__ == "__main__":
    main()
