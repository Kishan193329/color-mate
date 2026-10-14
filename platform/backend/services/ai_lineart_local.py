"""Local AI line-art generator — clean coloring-book line-art from any image, on CPU.

Runs a pretrained line-art model locally: no API, no GPU, no cost. This is what produces the
clean, subject-aware "coloring book" outline (detailed face/hair/clothes, backgrounds as
outlines) that classical edge detection (Canny/XDoG) cannot — those over-ink texture and leave
smooth subjects blank.

Models (weights auto-download once from HuggingFace `lllyasviel/Annotators`, cached in
`backend/models/`):
  - "lineart" (default) — Informative-Drawings, clean thin ink outlines. 17 MB, ~2-4 s / image.
  - "anime"             — Anime2Sketch, detailed pencil sketch. 217 MB, ~1 s / image.

Everything is best-effort: if torch or the weights are unavailable, `generate()` returns None so
the caller can fall back to the classical line-art. Nothing else in ColorForge is changed.
"""
from __future__ import annotations

import urllib.request
from pathlib import Path

import cv2
import numpy as np

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
WEIGHTS = {
    "lineart": ("lineart_sk_model.pth",
                "https://huggingface.co/lllyasviel/Annotators/resolve/main/sk_model.pth"),
    "anime": ("anime2sketch_netG.pth",
              "https://huggingface.co/lllyasviel/Annotators/resolve/main/netG.pth"),
}
DEFAULT_MODEL = "lineart"
_cache: dict = {}


def torch_available() -> bool:
    try:
        import torch  # noqa: F401
        return True
    except Exception:  # noqa: BLE001
        return False


def weights_path(model: str) -> Path:
    return MODELS_DIR / WEIGHTS[model][0]


def ensure_weights(model: str) -> Path | None:
    """Return the local weights path, downloading once if missing (needs internet)."""
    fn, url = WEIGHTS[model]
    dst = MODELS_DIR / fn
    if dst.exists():
        return dst
    try:
        MODELS_DIR.mkdir(parents=True, exist_ok=True)
        tmp = dst.with_suffix(".part")
        urllib.request.urlretrieve(url, tmp)
        tmp.replace(dst)
        return dst
    except Exception:  # noqa: BLE001 - offline / blocked: caller falls back
        return None


# ── vendored architectures (match the pretrained state_dicts exactly) ──────── #
def _build_lineart():
    import torch.nn as nn

    def norm(n):
        return nn.InstanceNorm2d(n)

    class ResBlock(nn.Module):
        def __init__(self, f):
            super().__init__()
            self.conv_block = nn.Sequential(
                nn.ReflectionPad2d(1), nn.Conv2d(f, f, 3), norm(f), nn.ReLU(True),
                nn.ReflectionPad2d(1), nn.Conv2d(f, f, 3), norm(f))

        def forward(self, x):
            return x + self.conv_block(x)

    class Generator(nn.Module):
        def __init__(self, inc=3, outc=1, nres=3):
            super().__init__()
            self.model0 = nn.Sequential(nn.ReflectionPad2d(3), nn.Conv2d(inc, 64, 7), norm(64), nn.ReLU(True))
            m1, i, o = [], 64, 128
            for _ in range(2):
                m1 += [nn.Conv2d(i, o, 3, 2, 1), norm(o), nn.ReLU(True)]; i, o = o, o * 2
            self.model1 = nn.Sequential(*m1)
            self.model2 = nn.Sequential(*[ResBlock(i) for _ in range(nres)])
            m3, o = [], i // 2
            for _ in range(2):
                m3 += [nn.ConvTranspose2d(i, o, 3, 2, 1, 1), norm(o), nn.ReLU(True)]; i, o = o, o // 2
            self.model3 = nn.Sequential(*m3)
            self.model4 = nn.Sequential(nn.ReflectionPad2d(3), nn.Conv2d(64, outc, 7), nn.Sigmoid())

        def forward(self, x):
            return self.model4(self.model3(self.model2(self.model1(self.model0(x)))))

    return Generator()


def _build_anime():
    import torch
    import torch.nn as nn

    def norm(n):
        return nn.InstanceNorm2d(n)

    class Skip(nn.Module):
        def __init__(self, outer, inner, input_nc=None, sub=None, outermost=False, innermost=False):
            super().__init__()
            self.outermost = outermost
            input_nc = input_nc or outer
            dc = nn.Conv2d(input_nc, inner, 4, 2, 1, bias=True)
            dr, dn = nn.LeakyReLU(0.2, True), norm(inner)
            ur, un = nn.ReLU(True), norm(outer)
            if outermost:
                m = [dc, sub, ur, nn.ConvTranspose2d(inner * 2, outer, 4, 2, 1), nn.Tanh()]
            elif innermost:
                m = [dr, dc, ur, nn.ConvTranspose2d(inner, outer, 4, 2, 1, bias=True), un]
            else:
                m = [dr, dc, dn, sub, ur, nn.ConvTranspose2d(inner * 2, outer, 4, 2, 1, bias=True), un]
            self.model = nn.Sequential(*m)

        def forward(self, x):
            return self.model(x) if self.outermost else torch.cat([x, self.model(x)], 1)

    class UnetG(nn.Module):
        def __init__(self, inc=3, outc=1, nd=8, ngf=64):
            super().__init__()
            u = Skip(ngf * 8, ngf * 8, sub=None, innermost=True)
            for _ in range(nd - 5):
                u = Skip(ngf * 8, ngf * 8, sub=u)
            u = Skip(ngf * 4, ngf * 8, sub=u)
            u = Skip(ngf * 2, ngf * 4, sub=u)
            u = Skip(ngf, ngf * 2, sub=u)
            self.model = Skip(outc, ngf, input_nc=inc, sub=u, outermost=True)

        def forward(self, x):
            return self.model(x)

    return UnetG()


def _load(model: str):
    if model in _cache:
        return _cache[model]
    import torch
    w = ensure_weights(model)
    if w is None:
        return None
    net = _build_anime() if model == "anime" else _build_lineart()
    sd = torch.load(w, map_location="cpu", weights_only=True)
    sd = {k.replace("module.", ""): v for k, v in sd.items()}
    net.load_state_dict(sd, strict=True)
    net.eval()
    _cache[model] = net
    return net


def generate(image_path: Path, out_path: Path | None = None, model: str = DEFAULT_MODEL,
             size: int = 1024, darken: float = 1.35) -> np.ndarray | None:
    """Clean line-art (white paper, dark lines) for an image, at its original resolution.
    `darken` (>1) crisps the ink toward pure black. Returns the grayscale image, or None if
    the model is unavailable (caller should fall back to classical line-art)."""
    if not torch_available():
        return None
    try:
        import torch
        net = _load(model)
        if net is None:
            return None
        img = cv2.imread(str(image_path))
        if img is None:
            return None
        h, w = img.shape[:2]
        s = max(h, w)
        pad = np.full((s, s, 3), 255, np.uint8)
        pad[:h, :w] = img
        rgb = cv2.cvtColor(cv2.resize(pad, (size, size)), cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        if model == "anime":
            t = torch.from_numpy((rgb - 0.5) / 0.5).permute(2, 0, 1).unsqueeze(0)
        else:
            t = torch.from_numpy(rgb).permute(2, 0, 1).unsqueeze(0)
        with torch.no_grad():
            o = net(t)[0, 0].numpy()
        line = (o * 0.5 + 0.5) * 255 if model == "anime" else o * 255  # both -> white paper, dark line
        line = np.clip(line, 0, 255).astype(np.uint8)
        line = cv2.resize(line, (s, s), interpolation=cv2.INTER_AREA)[:h, :w]
        if darken and darken != 1.0:                       # crisp the ink toward black
            line = np.clip(255 - (255.0 - line) * darken, 0, 255).astype(np.uint8)
        if out_path is not None:
            cv2.imwrite(str(out_path), line)
        return line
    except Exception:  # noqa: BLE001 - any failure -> classical fallback
        return None
