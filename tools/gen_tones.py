"""Generates the cue tones in app/src/main/res/raw (16-bit mono 44.1 kHz WAV)."""
import math
import os
import struct
import sys
import traceback
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "app", "src", "main", "res", "raw")
RATE = 44100


def tone(path, freq_hz, ms, volume=0.6, fade_ms=5):
    n = int(RATE * ms / 1000)
    fade = int(RATE * fade_ms / 1000)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = bytearray()
        for i in range(n):
            env = min(1.0, i / fade, (n - 1 - i) / fade) if fade else 1.0
            sample = volume * env * math.sin(2 * math.pi * freq_hz * i / RATE)
            frames += struct.pack("<h", int(sample * 32767))
        w.writeframes(bytes(frames))


def main():
    os.makedirs(RAW, exist_ok=True)
    tone(os.path.join(RAW, "tone_short.wav"), 1000, 120)
    tone(os.path.join(RAW, "tone_long.wav"), 1500, 600)
    print("wrote", os.path.abspath(RAW))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        with open(os.path.join(HERE, "error.txt"), "w", encoding="utf-8") as f:
            f.write(traceback.format_exc())
        sys.exit(1)
