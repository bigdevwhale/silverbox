"""Synthesises the projector's clatter (res/raw/projector.wav): a seamless 2 s loop.

python tools/projector.py

18 claw pulls a second (the speed of home movie), soft and muffled, over a quiet motor purr.
Nothing recorded, so nothing to license.
"""
import wave
from pathlib import Path

import numpy as np

RATE = 22050
SECONDS = 2.0
FPS = 18

n = int(RATE * SECONDS)
t = np.arange(n) / RATE
rng = np.random.default_rng(8)

out = np.zeros(n)
# The claw: a soft, muffled flutter rather than a tick. Low-pitched knocks with no sharp attack,
# heard as if through the wall of the projector's case.
period = RATE / FPS
L = int(RATE * 0.045)
tt = np.arange(L) / RATE
env = (1 - np.exp(-tt * 900)) * np.exp(-tt * 140)
for k in range(int(SECONDS * FPS)):
    at = int(k * period + rng.normal(0, 25))
    if at < 0 or at + L > n:
        continue
    f = 180 + rng.normal(0, 12)
    out[at:at + L] += (0.5 + 0.1 * rng.random()) * env * np.sin(2 * np.pi * f * tt)
# A quiet, low motor purr (whole cycles in two seconds, so the loop is seamless); no hiss.
out += 0.035 * np.sin(2 * np.pi * 60 * t)
# Muffle: a gentle low-pass.
kernel = np.ones(24) / 24
out = np.convolve(out, kernel, mode="same")
out /= np.max(np.abs(out)) * 1.6

dst = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/projector.wav"
with wave.open(str(dst), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes((out * 32767).astype("<i2").tobytes())
print(dst, dst.stat().st_size)
