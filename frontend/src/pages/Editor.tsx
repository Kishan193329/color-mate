import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import GameCanvas from "../components/GameCanvas";
import SemanticCanvas, { FILTERS, LABEL_STYLE } from "../components/SemanticCanvas";
import LineArtPanel from "../components/LineArtPanel";
import CorrectionCanvas from "../components/CorrectionCanvas";
import {
  downloadUrl,
  getGameConfig,
  getProgress,
  getQualityReport,
  runCleanup,
  saveProgress,
  smartGroup,
  type CleanupAction,
  type GameConfig,
  type QualityReport,
} from "../api/client";
import { clearLocal, loadLocal, saveLocal } from "../lib/progressStore";

type Mode =
  | "original"
  | "lineart"
  | "regions"
  | "texture"
  | "palette"
  | "numbers"
  | "semantic"
  | "correction";

const MODES: { id: Mode; label: string }[] = [
  { id: "texture", label: "Texture Fill" },
  { id: "palette", label: "Palette Fill" },
  { id: "regions", label: "Regions" },
  { id: "numbers", label: "Numbers" },
  { id: "semantic", label: "Semantic View" },
  { id: "correction", label: "Correction" },
  { id: "lineart", label: "Line Art" },
  { id: "original", label: "Original" },
];

// Interactive game modes (clickable canvas) vs static image views.
const INTERACTIVE: Mode[] = ["texture", "palette"];
const isInteractive = (m: Mode) => INTERACTIVE.includes(m);

// Which artifact a static view shows.
const STATIC_ARTIFACT: Record<string, string> = {
  original: "original",
  lineart: "lineart",
  regions: "region_mask",
  numbers: "preview",
};

const MODE_HINT: Partial<Record<Mode, string>> = {
  texture: "Texture Fill keeps the original artwork look.",
  palette: "Palette Fill creates simplified color-by-number colors.",
};

export default function Editor() {
  const { jobId } = useParams<{ jobId: string }>();
  const [config, setConfig] = useState<GameConfig | null>(null);
  const [mode, setMode] = useState<Mode>("palette");
  const [selected, setSelected] = useState<number | null>(null);
  const [filled, setFilled] = useState<Set<number>>(new Set());
  const [quality, setQuality] = useState<QualityReport | null>(null);
  const [busy, setBusy] = useState<CleanupAction | null>(null);
  const [filters, setFilters] = useState<Set<string>>(new Set());
  const [grouping, setGrouping] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load config + merge backend / local progress + quality report.
  const loadAll = async (mergeLocal: boolean) => {
    if (!jobId) return;
    const cfg = await getGameConfig(jobId);
    setConfig(cfg);
    let serverFilled: number[] = [];
    try {
      serverFilled = (await getProgress(jobId)).filled;
    } catch {
      /* no progress yet */
    }
    const ids = mergeLocal ? [...serverFilled, ...loadLocal(jobId)] : serverFilled;
    setFilled(new Set(ids));
    setSelected(cfg.palette[0]?.number ?? null);
    try {
      setQuality(await getQualityReport(jobId));
    } catch {
      setQuality(null);
    }
  };

  useEffect(() => {
    if (!jobId) return;
    loadAll(true).catch((e) =>
      setError(e instanceof Error ? e.message : "Could not load game")
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId]);

  // Cleanup actions re-run the pipeline server-side; reload everything after.
  const handleCleanup = async (action: CleanupAction) => {
    if (!jobId || busy) return;
    setBusy(action);
    setError(null);
    try {
      const report = await runCleanup(jobId, action);
      setQuality(report);
      clearLocal(jobId); // server reset fills (region ids changed)
      await loadAll(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cleanup failed");
    } finally {
      setBusy(null);
    }
  };

  // Persist: localStorage immediately, backend debounced.
  const persist = (next: Set<number>) => {
    if (!jobId) return;
    const arr = [...next];
    saveLocal(jobId, arr);
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      saveProgress(jobId, arr).catch(() => {});
    }, 500);
  };

  const handleFill = (regionId: number) => {
    setFilled((prev) => {
      if (prev.has(regionId)) return prev;
      const next = new Set(prev);
      next.add(regionId);
      persist(next);
      return next;
    });
  };

  const handleReset = () => {
    if (!jobId) return;
    const empty = new Set<number>();
    setFilled(empty);
    clearLocal(jobId);
    saveProgress(jobId, []).catch(() => {});
  };

  const toggleFilter = (cat: string) =>
    setFilters((prev) => {
      const next = new Set(prev);
      next.has(cat) ? next.delete(cat) : next.add(cat);
      return next;
    });

  const handleSmartGroup = async () => {
    if (!jobId || grouping) return;
    setGrouping(true);
    setError(null);
    try {
      await smartGroup(jobId);
      setConfig(await getGameConfig(jobId)); // refresh semantic fields, keep fills
    } catch (e) {
      setError(e instanceof Error ? e.message : "Smart group failed");
    } finally {
      setGrouping(false);
    }
  };

  // Per-number remaining counts (how many regions of each color are unfilled).
  const remaining = useMemo(() => {
    const map = new Map<number, number>();
    if (!config) return map;
    for (const r of config.regions) {
      if (!filled.has(r.id)) map.set(r.color_id, (map.get(r.color_id) ?? 0) + 1);
    }
    return map;
  }, [config, filled]);

  const total = config?.regions.length ?? 0;
  const percent = total ? Math.round((filled.size / total) * 100) : 0;
  const done = total > 0 && filled.size === total;

  if (error) {
    return <div className="card mx-auto max-w-md text-center text-red-600">{error}</div>;
  }
  if (!jobId || !config) {
    return <div className="text-center text-slate-400">Loading game…</div>;
  }

  return (
    <div className="space-y-5">
      {/* Header + progress */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight">Color Studio</h1>
          <p className="text-sm text-slate-500">
            {config.regions.length} regions · {config.palette.length} colors
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn-ghost py-2" onClick={handleReset}>
            ⟲ Reset
          </button>
          <Link to={`/export/${jobId}`} className="btn-primary py-2">
            Export →
          </Link>
        </div>
      </div>

      <div className="card !p-4">
        <div className="mb-2 flex items-center justify-between text-sm font-medium">
          <span className="text-slate-500">Progress</span>
          <span className="text-forge-700">
            {filled.size}/{total} · {percent}%
          </span>
        </div>
        <div className="h-3 overflow-hidden rounded-full bg-slate-100">
          <motion.div
            className="h-full rounded-full bg-forge-600"
            animate={{ width: `${percent}%` }}
            transition={{ duration: 0.4 }}
          />
        </div>
        {done && (
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="mt-2 text-center text-sm font-semibold text-emerald-600"
          >
            🎉 Artwork complete!
          </motion.p>
        )}
      </div>

      {/* Quality panel */}
      <div className="card !p-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="relative grid h-16 w-16 place-items-center">
              <svg className="absolute inset-0 -rotate-90" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="16" fill="none" stroke="#e2e8f0" strokeWidth="3" />
                <motion.circle
                  cx="18" cy="18" r="16" fill="none" stroke="#7c3aed" strokeWidth="3"
                  strokeLinecap="round"
                  strokeDasharray={2 * Math.PI * 16}
                  animate={{
                    strokeDashoffset:
                      2 * Math.PI * 16 * (1 - (quality?.score ?? 0) / 100),
                  }}
                  transition={{ duration: 0.6 }}
                />
              </svg>
              <span className="text-lg font-extrabold text-forge-700">
                {quality?.score ?? "–"}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
              <QStat label="Regions" value={quality?.regions_count ?? config.regions.length} />
              <QStat label="Tiny removed" value={quality?.tiny_regions_removed ?? 0} />
              <QStat label="Tiny merged" value={quality?.tiny_regions_merged ?? 0} />
              <QStat label="Gaps repaired" value={quality?.gaps_repaired ?? 0} />
            </div>
          </div>
          <span className="chip bg-forge-50 text-forge-700">
            {quality?.profile ?? "medium"} difficulty
          </span>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <CleanBtn label="Auto Clean" action="auto-clean" busy={busy} onClick={handleCleanup} primary />
          <CleanBtn label="Make Easier" action="make-easier" busy={busy} onClick={handleCleanup} />
          <CleanBtn label="Make Detailed" action="make-detailed" busy={busy} onClick={handleCleanup} />
          <CleanBtn label="Undo Cleanup" action="undo-cleanup" busy={busy} onClick={handleCleanup} ghost />
        </div>
        {quality?.notes?.length ? (
          <p className="mt-2 text-xs text-slate-400">{quality.notes.join(" ")}</p>
        ) : null}
      </div>

      {/* Mode toggles */}
      <div className="flex flex-wrap gap-2">
        {MODES.map((m) => (
          <button
            key={m.id}
            onClick={() => setMode(m.id)}
            className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
              mode === m.id
                ? "bg-forge-600 text-white shadow-glow"
                : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50"
            }`}
          >
            {m.label}
          </button>
        ))}
      </div>

      {MODE_HINT[mode] && (
        <p className="-mt-1 text-sm text-slate-500">{MODE_HINT[mode]}</p>
      )}

      {/* Canvas */}
      {isInteractive(mode) ? (
        <GameCanvas
          config={config}
          mode={mode === "texture" ? "texture" : "game"}
          selectedNumber={selected}
          filled={filled}
          textureUrl={downloadUrl(jobId, "preprocessed")}
          onFill={handleFill}
        />
      ) : mode === "semantic" ? (
        <SemanticView
          config={config}
          filters={filters}
          onToggle={toggleFilter}
          onSmartGroup={handleSmartGroup}
          grouping={grouping}
        />
      ) : mode === "lineart" ? (
        <LineArtPanel jobId={jobId} onApplied={() => loadAll(false)} />
      ) : mode === "correction" ? (
        <CorrectionCanvas jobId={jobId} config={config} onChanged={() => loadAll(false)} />
      ) : (
        <div className="grid h-[64vh] place-items-center overflow-hidden rounded-2xl bg-slate-50 ring-1 ring-slate-200">
          <img
            src={downloadUrl(jobId, STATIC_ARTIFACT[mode])}
            alt={mode}
            className="max-h-full max-w-full object-contain"
          />
        </div>
      )}

      {/* Bottom palette number strip */}
      {isInteractive(mode) && (
        <div className="card !p-3">
          <div className="flex gap-2 overflow-x-auto pb-1">
            {config.palette.map((c) => {
              const left = remaining.get(c.id) ?? 0;
              const isDone = left === 0;
              const isSel = selected === c.number;
              return (
                <button
                  key={c.id}
                  onClick={() => setSelected(c.number)}
                  className={`relative flex shrink-0 flex-col items-center gap-1 rounded-xl px-3 py-2 transition ${
                    isSel
                      ? "bg-forge-50 ring-2 ring-forge-500"
                      : "ring-1 ring-slate-200 hover:bg-slate-50"
                  } ${isDone ? "opacity-40" : ""}`}
                >
                  <span
                    className="grid h-8 w-8 place-items-center rounded-full text-xs font-bold text-white ring-1 ring-black/10"
                    style={{ background: c.color }}
                  >
                    {c.number}
                  </span>
                  <span className="text-[10px] font-medium text-slate-500">
                    {isDone ? "✓" : left}
                  </span>
                </button>
              );
            })}
          </div>
          <p className="mt-2 text-center text-xs text-slate-400">
            Pick a number, then tap the matching regions · scroll to zoom · drag to pan
          </p>
        </div>
      )}
    </div>
  );
}

function SemanticView({
  config,
  filters,
  onToggle,
  onSmartGroup,
  grouping,
}: {
  config: GameConfig;
  filters: Set<string>;
  onToggle: (cat: string) => void;
  onSmartGroup: () => void;
  grouping: boolean;
}) {
  const hasSemantics = config.regions.some((r) => r.semantic_label);
  // Which labels actually appear, for a compact legend.
  const present = Array.from(
    new Set(config.regions.map((r) => r.semantic_label).filter(Boolean) as string[])
  );
  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-500">
        Semantic View groups regions into logical parts (a helper layer — line-art
        regions stay the source of truth).
      </p>

      <div className="flex flex-wrap items-center gap-2">
        {FILTERS.map((f) => {
          const on = filters.has(f);
          return (
            <button
              key={f}
              onClick={() => onToggle(f)}
              className={`rounded-lg px-3 py-1.5 text-sm font-semibold transition ${
                on
                  ? "bg-forge-600 text-white shadow-glow"
                  : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50"
              }`}
            >
              {f}
            </button>
          );
        })}
        <button
          className="btn-primary ml-auto py-2 text-sm"
          disabled={grouping}
          onClick={onSmartGroup}
        >
          {grouping ? "Grouping…" : "✦ Smart Group Regions"}
        </button>
      </div>

      <SemanticCanvas config={config} active={filters} />

      {hasSemantics ? (
        <div className="flex flex-wrap gap-3">
          {present.map((lab) => (
            <span key={lab} className="flex items-center gap-1.5 text-xs text-slate-500">
              <span
                className="h-3 w-3 rounded-sm ring-1 ring-black/10"
                style={{ background: LABEL_STYLE[lab]?.color ?? "#E2E8F0" }}
              />
              {lab}
            </span>
          ))}
        </div>
      ) : (
        <p className="text-sm text-slate-400">
          No semantic labels yet — click “Smart Group Regions”.
        </p>
      )}
    </div>
  );
}

function QStat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div className="font-bold text-slate-800">{value}</div>
      <div className="text-[11px] text-slate-400">{label}</div>
    </div>
  );
}

function CleanBtn({
  label,
  action,
  busy,
  onClick,
  primary,
  ghost,
}: {
  label: string;
  action: CleanupAction;
  busy: CleanupAction | null;
  onClick: (a: CleanupAction) => void;
  primary?: boolean;
  ghost?: boolean;
}) {
  const isBusy = busy === action;
  const base = primary ? "btn-primary" : "btn-ghost";
  return (
    <button
      className={`${base} py-2 text-sm ${ghost ? "text-slate-500" : ""}`}
      disabled={busy !== null}
      onClick={() => onClick(action)}
    >
      {isBusy ? "Working…" : label}
    </button>
  );
}
