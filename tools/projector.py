"""Synthesises the projector's clatter (res/raw/projector.wav): a seamless 2 s loop.

python tools/projector.py

18 claw pulls a second (the speed of home movie), each a short damped click, over the hum of the
motor and the hiss of the fan. Nothing recorded, so nothing to license.
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
# The claw: a sharp tick and a softer knock, every frame, slightly uneven.
period = RATE / FPS
for k in range(int(SECONDS * FPS)):
    at = int(k * period + rng.normal(0, 18))
    for delay, amp, freq, decay in ((0, 0.55, 2400, 900), (int(RATE * 0.012), 0.35, 900, 400)):
        s = at + delay
        L = int(RATE * 0.03)
        if s < 0 or s + L > n:
            continue
        tt = np.arange(L) / RATE
        click = amp * np.sin(2 * np.pi * freq * tt) * np.exp(-tt * decay)
        click += amp * 0.6 * rng.normal(0, 1, L) * np.exp(-tt * decay * 1.5)
        out[s:s + L] += click
# Motor hum (whole cycles in two seconds, so the loop is seamless) and fan hiss.
out += 0.06 * np.sin(2 * np.pi * 100 * t) + 0.03 * np.sin(2 * np.pi * 150 * t)
hiss = rng.normal(0, 1, n)
hiss = np.convolve(hiss, np.ones(6) / 6, mode="same")
out += 0.05 * hiss
out /= np.max(np.abs(out)) * 1.15

dst = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/projector.wav"
with wave.open(str(dst), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes((out * 32767).astype("<i2").tobytes())
print(dst, dst.stat().st_size)
