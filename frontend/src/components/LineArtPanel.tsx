import { useEffect, useState } from "react";
import {
  downloadUrl,
  getLineartQuality,
  previewLineart,
  regenerateLineart,
  runCleanup,
  type LineartQuality,
  type LineartSettings,
} from "../api/client";

const FALLBACK_MODES = [
  { id: "simple_cartoon", label: "Simple Cartoon" },
  { id: "detailed_artwork", label: "Detailed Artwork" },
  { id: "mandala_pattern", label: "Mandala / Pattern" },
  { id: "animal_fur", label: "Animal Fur" },
  { id: "portrait", label: "Portrait" },
  { id: "object_product", label: "Object / Product" },
];

type Busy = "preview" | "apply" | "undo" | null;

export default function LineArtPanel({
  jobId,
  onApplied,
}: {
  jobId: string;
  onApplied: () => void;
}) {
  const [quality, setQuality] = useState<LineartQuality | null>(null);
  const [s, setS] = useState<Required<LineartSettings>>({
    mode: "detailed_artwork",
    thickness: 1.3,
    detail: 0.7,
    gap: 0.6,
    remove_noise: true,
  });
  const [busy, setBusy] = useState<Busy>(null);
  const [previewing, setPreviewing] = useState(false);
  const [version, setVersion] = useState(0); // cache-bust the image
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const q = await getLineartQuality(jobId);
      setQuality(q);
      if (q.settings) setS((prev) => ({ ...prev, ...q.settings }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load line-art quality");
    }
  };
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId]);

  const modes = quality?.modes?.length ? quality.modes : FALLBACK_MODES;

  const doPreview = async () => {
    setBusy("preview");
    setError(null);
    try {
      const r = await previewLineart(jobId, s);
      setQuality((q) => ({ ...(q as LineartQuality), ...r.quality }));
      setPreviewing(true);
      setVersion((v) => v + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Preview failed");
    } finally {
      setBusy(null);
    }
  };

  const doApply = async () => {
    setBusy("apply");
    setError(null);
    try {
      const r = await regenerateLineart(jobId, s);
      setQuality((q) => ({ ...(q as LineartQuality), ...r.quality }));
      setPreviewing(false);
      setVersion((v) => v + 1);
      onApplied(); // regions changed -> parent reloads game config
    } catch (e) {
      setError(e instanceof Error ? e.message : "Regenerate failed");
    } finally {
      setBusy(null);
    }
  };

  const doUndo = async () => {
    setBusy("undo");
    setError(null);
    try {
      await runCleanup(jobId, "undo-cleanup");
      setPreviewing(false);
      setVersion((v) => v + 1);
      await load();
      onApplied();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Nothing to undo");
    } finally {
      setBusy(null);
    }
  };

  const imgType = previewing ? "lineart_preview" : "lineart";
  const imgSrc = `${downloadUrl(jobId, imgType)}?v=${version}`;

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
      {/* Image */}
      <div className="space-y-2">
        <div className="grid h-[60vh] place-items-center overflow-hidden rounded-2xl bg-slate-50 ring-1 ring-slate-200">
          <img src={imgSrc} alt="line art" className="max-h-full max-w-full object-contain" />
        </div>
        <p className="text-center text-xs text-slate-400">
          {previewing ? "Previewing candidate — click Apply to keep it." : "Current line art"}
        </p>
      </div>

      {/* Controls */}
      <aside className="card space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold uppercase tracking-wide text-slate-400">
            Line Art
          </h3>
          {quality && (
            <span className="chip bg-forge-50 text-forge-700">
              Quality {quality.score}
            </span>
          )}
        </div>

        <label className="block text-sm">
          <span className="font-medium text-slate-600">Mode</span>
          <select
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm"
            value={s.mode}
            onChange={(e) => setS({ ...s, mode: e.target.value })}
          >
            {modes.map((m) => (
              <option key={m.id} value={m.id}>
                {m.label}
              </option>
            ))}
          </select>
        </label>

        <Slider
          label="Line thickness"
          min={0.5}
          max={3}
          step={0.1}
          value={s.thickness}
          onChange={(v) => setS({ ...s, thickness: v })}
        />
        <Slider
          label="Detail level"
          min={0}
          max={1}
          step={0.05}
          value={s.detail}
          onChange={(v) => setS({ ...s, detail: v })}
        />
        <Slider
          label="Gap repair strength"
          min={0}
          max={1}
          step={0.05}
          value={s.gap}
          onChange={(v) => setS({ ...s, gap: v })}
        />

        <label className="flex items-center justify-between text-sm">
          <span className="font-medium text-slate-600">Remove noise</span>
          <input
            type="checkbox"
            checked={s.remove_noise}
            onChange={(e) => setS({ ...s, remove_noise: e.target.checked })}
            className="h-4 w-8 accent-forge-600"
          />
        </label>

        {quality && (
          <p className="text-xs text-slate-400">
            density {quality.line_density} · {quality.fragments} fragments ·{" "}
            {quality.isolated_dots} dots
          </p>
        )}

        <div className="space-y-2 pt-1">
          <button className="btn-ghost w-full py-2 text-sm" disabled={busy !== null} onClick={doPreview}>
            {busy === "preview" ? "Rendering…" : "Preview"}
          </button>
          <button className="btn-primary w-full py-2 text-sm" disabled={busy !== null} onClick={doApply}>
            {busy === "apply" ? "Regenerating…" : "Regenerate Line Art"}
          </button>
          <button className="btn-ghost w-full py-2 text-sm text-slate-500" disabled={busy !== null} onClick={doUndo}>
            {busy === "undo" ? "Undoing…" : "↶ Undo"}
          </button>
        </div>

        {error && <p className="text-xs text-red-600">{error}</p>}
      </aside>
    </div>
  );
}

function Slider({
  label,
  min,
  max,
  step,
  value,
  onChange,
}: {
  label: string;
  min: number;
  max: number;
  step: number;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <label className="block text-sm">
      <span className="flex justify-between">
        <span className="font-medium text-slate-600">{label}</span>
        <span className="text-slate-400">{value}</span>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="mt-1 w-full accent-forge-600"
      />
    </label>
  );
}
