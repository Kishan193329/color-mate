import { useEffect, useRef, useState } from "react";
import {
  deleteRegions,
  downloadUrl,
  getVersions,
  mergeRegions,
  recalcRegions,
  redoEdit,
  saveLineart,
  splitRegion,
  undoEdit,
  updateRegionColor,
  updateRegionLabel,
  type GameConfig,
  type VersionState,
} from "../api/client";

type Tool = "pan" | "select" | "draw" | "erase" | "split";

const TOOLS: { id: Tool; label: string }[] = [
  { id: "select", label: "Select" },
  { id: "draw", label: "Fix Line" },
  { id: "erase", label: "Erase" },
  { id: "split", label: "Split Area" },
  { id: "pan", label: "Pan" },
];

const MIN_SCALE = 0.2;
const MAX_SCALE = 8;
const LABEL_HIT = 16;
const TINY_AREA = 400;

export default function CorrectionCanvas({
  jobId,
  config,
  onChanged,
}: {
  jobId: string;
  config: GameConfig;
  onChanged: () => Promise<void> | void;
}) {
  const { width, height, regions, palette } = config;
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const [tool, setTool] = useState<Tool>("select");
  const [brush, setBrush] = useState(4);
  const [opacity, setOpacity] = useState(0.35);
  const [showLineart, setShowLineart] = useState(true);
  const [showRegions, setShowRegions] = useState(true);
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [dirtyLine, setDirtyLine] = useState(false);
  const [versions, setVersions] = useState<VersionState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [bust, setBust] = useState(0);

  // view transform
  const [scale, setScale] = useState(1);
  const [tx, setTx] = useState(0);
  const [ty, setTy] = useState(0);

  // interaction refs
  const drawing = useRef(false);
  const last = useRef({ x: 0, y: 0 });
  const moved = useRef(false);
  const splitPts = useRef<number[][]>([]);
  const dragLabel = useRef<number | null>(null);
  const paths = useRef<Map<number, Path2D>>(new Map());

  // load the line art into the editable canvas (and on every external change)
  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    cv.width = width;
    cv.height = height;
    const img = new Image();
    img.onload = () => cv.getContext("2d")?.drawImage(img, 0, 0, width, height);
    img.src = `${downloadUrl(jobId, "lineart")}?b=${bust}`;
    setDirtyLine(false);
  }, [jobId, width, height, bust]);

  useEffect(() => {
    paths.current = new Map(regions.map((r) => [r.id, new Path2D(r.path)]));
  }, [regions]);

  useEffect(() => {
    getVersions(jobId).then(setVersions).catch(() => {});
  }, [jobId, bust]);

  // fit on first mount
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const s = Math.min(el.clientWidth / width, el.clientHeight / height) * 0.98;
    setScale(s);
    setTx((el.clientWidth - width * s) / 2);
    setTy((el.clientHeight - height * s) / 2);
  }, [width, height]);

  const toImage = (cx: number, cy: number) => {
    const rect = containerRef.current!.getBoundingClientRect();
    return { x: (cx - rect.left - tx) / scale, y: (cy - rect.top - ty) / scale };
  };

  const refresh = async () => {
    setSelected([]);
    setBust((b) => b + 1);
    await onChanged();
  };

  // ---- pointer handling ----------------------------------------------------
  const onDown = (e: React.PointerEvent) => {
    (e.target as Element).setPointerCapture?.(e.pointerId);
    moved.current = false;
    const p = toImage(e.clientX, e.clientY);
    last.current = { x: e.clientX, y: e.clientY };

    if (tool === "draw" || tool === "erase") {
      drawing.current = true;
      paintTo(p.x, p.y, p.x, p.y);
    } else if (tool === "split") {
      drawing.current = true;
      splitPts.current = [[p.x, p.y]];
    } else if (tool === "select") {
      // grab a label?
      const lbl = regions.find(
        (r) => r.visible && Math.hypot(r.label_pos.x - p.x, r.label_pos.y - p.y) < LABEL_HIT / scale
      );
      dragLabel.current = lbl ? lbl.id : null;
    }
  };

  const onMove = (e: React.PointerEvent) => {
    const dx = e.clientX - last.current.x;
    const dy = e.clientY - last.current.y;
    if (Math.abs(dx) + Math.abs(dy) > 2) moved.current = true;
    const p = toImage(e.clientX, e.clientY);

    if (tool === "pan" && (e.buttons & 1)) {
      setTx((v) => v + dx); setTy((v) => v + dy);
    } else if (drawing.current && (tool === "draw" || tool === "erase")) {
      const prev = toImage(last.current.x, last.current.y);
      paintTo(prev.x, prev.y, p.x, p.y);
    } else if (drawing.current && tool === "split") {
      splitPts.current.push([p.x, p.y]);
    } else if (tool === "select" && dragLabel.current != null && (e.buttons & 1)) {
      // live-move handled on drop; nothing visual here for simplicity
    }
    last.current = { x: e.clientX, y: e.clientY };
  };

  const onUp = async (e: React.PointerEvent) => {
    const p = toImage(e.clientX, e.clientY);
    if (tool === "draw" || tool === "erase") {
      drawing.current = false;
      setDirtyLine(true);
    } else if (tool === "split") {
      drawing.current = false;
      const pts = splitPts.current;
      splitPts.current = [];
      if (selected.length === 1 && pts.length >= 2) {
        await runEdit("Splitting…", () => splitRegion(jobId, selected[0], pts));
      } else {
        setError("Select exactly one region, then draw a line across it to split.");
      }
    } else if (tool === "select") {
      if (dragLabel.current != null && moved.current) {
        const id = dragLabel.current;
        await runEdit("Moving label…", () =>
          updateRegionLabel(jobId, id, { label_pos: { x: Math.round(p.x), y: Math.round(p.y) } })
        );
      } else if (!moved.current) {
        const hit = hitRegion(p.x, p.y);
        if (hit != null)
          setSelected((s) => (s.includes(hit) ? s.filter((i) => i !== hit) : [...s, hit]));
      }
      dragLabel.current = null;
    }
  };

  const onWheel = (e: React.WheelEvent) => {
    const rect = containerRef.current!.getBoundingClientRect();
    const px = e.clientX - rect.left, py = e.clientY - rect.top;
    const factor = e.deltaY < 0 ? 1.1 : 0.9;
    setScale((s) => {
      const ns = Math.min(MAX_SCALE, Math.max(MIN_SCALE, s * factor));
      const k = ns / s;
      setTx((x) => px - (px - x) * k);
      setTy((y) => py - (py - y) * k);
      return ns;
    });
  };

  const paintTo = (x0: number, y0: number, x1: number, y1: number) => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.lineWidth = brush;
    ctx.strokeStyle = tool === "erase" ? "#ffffff" : "#111111";
    ctx.beginPath();
    ctx.moveTo(x0, y0);
    ctx.lineTo(x1, y1);
    ctx.stroke();
  };

  const hitRegion = (x: number, y: number): number | null => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return null;
    // topmost (smallest) region wins for nicer UX
    const ordered = [...regions].sort((a, b) => a.area - b.area);
    for (const r of ordered) {
      const path = paths.current.get(r.id);
      if (path && ctx.isPointInPath(path, x, y)) return r.id;
    }
    return null;
  };

  // ---- edit actions --------------------------------------------------------
  const runEdit = async (label: string, fn: () => Promise<unknown>) => {
    setBusy(label);
    setError(null);
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Edit failed");
    } finally {
      setBusy(null);
    }
  };

  const applyLineEdits = () =>
    runEdit("Applying line edits…", async () => {
      const blob: Blob = await new Promise((res) =>
        canvasRef.current!.toBlob((b) => res(b!), "image/png")
      );
      await saveLineart(jobId, blob);
      await recalcRegions(jobId);
    });

  return (
    <div className="space-y-3">
      {/* Toolbar */}
      <div className="card !p-3 space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          {TOOLS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTool(t.id)}
              className={`rounded-lg px-3 py-1.5 text-sm font-semibold transition ${
                tool === t.id
                  ? "bg-forge-600 text-white shadow-glow"
                  : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50"
              }`}
            >
              {t.label}
            </button>
          ))}
          <div className="mx-1 h-6 w-px bg-slate-200" />
          <button
            className="btn-primary py-1.5 text-sm disabled:opacity-50"
            disabled={!dirtyLine || busy !== null}
            onClick={applyLineEdits}
          >
            Apply Line Edits
          </button>
          <div className="ml-auto flex items-center gap-1">
            <button
              className="zoombtn !h-8 !w-8 !text-sm"
              disabled={!versions?.can_undo || busy !== null}
              onClick={() => runEdit("Undo…", () => undoEdit(jobId))}
              title="Undo"
            >
              ↶
            </button>
            <button
              className="zoombtn !h-8 !w-8 !text-sm"
              disabled={!versions?.can_redo || busy !== null}
              onClick={() => runEdit("Redo…", () => redoEdit(jobId))}
              title="Redo"
            >
              ↷
            </button>
          </div>
        </div>

        {/* Region actions */}
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-slate-400">{selected.length} selected</span>
          <button
            className="btn-ghost py-1.5 text-xs"
            disabled={selected.length < 2 || busy !== null}
            onClick={() => runEdit("Merging…", () => mergeRegions(jobId, selected))}
          >
            Merge Areas
          </button>
          <button
            className="btn-ghost py-1.5 text-xs"
            disabled={selected.length === 0 || busy !== null}
            onClick={() => runEdit("Deleting…", () => deleteRegions(jobId, { region_ids: selected }))}
          >
            Delete
          </button>
          <button
            className="btn-ghost py-1.5 text-xs"
            disabled={selected.length !== 1 || busy !== null}
            onClick={() => {
              const r = regions.find((x) => x.id === selected[0]);
              if (r) runEdit("Toggling…", () =>
                updateRegionLabel(jobId, r.id, { visible: !r.visible }));
            }}
          >
            Hide/Show #
          </button>
          <button
            className="btn-ghost py-1.5 text-xs"
            disabled={busy !== null}
            onClick={() => runEdit("Cleaning…", () => deleteRegions(jobId, { min_area: TINY_AREA }))}
          >
            Clean Tiny Parts
          </button>
          {/* change color number */}
          {selected.length === 1 && (
            <span className="flex items-center gap-1">
              <span className="text-slate-400">→</span>
              {palette.slice(0, 12).map((p) => (
                <button
                  key={p.id}
                  title={`Set to ${p.number}`}
                  onClick={() => runEdit("Recoloring…", () =>
                    updateRegionColor(jobId, selected[0], p.id))}
                  className="h-5 w-5 rounded ring-1 ring-black/10"
                  style={{ background: p.color }}
                />
              ))}
            </span>
          )}
        </div>

        {/* Layer + brush controls */}
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-slate-500">
          <label className="flex items-center gap-2">
            Original
            <input type="range" min={0} max={1} step={0.05} value={opacity}
              onChange={(e) => setOpacity(Number(e.target.value))} className="accent-forge-600" />
          </label>
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={showLineart}
              onChange={(e) => setShowLineart(e.target.checked)} /> Line art
          </label>
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={showRegions}
              onChange={(e) => setShowRegions(e.target.checked)} /> Regions
          </label>
          <label className="flex items-center gap-2">
            Brush
            <input type="range" min={1} max={20} step={1} value={brush}
              onChange={(e) => setBrush(Number(e.target.value))} className="accent-forge-600" />
            <span>{brush}px</span>
          </label>
          {busy && <span className="text-forge-600">{busy}</span>}
        </div>
        {error && <p className="text-xs text-red-600">{error}</p>}
      </div>

      {/* Canvas stack */}
      <div
        ref={containerRef}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onWheel={onWheel}
        className="relative h-[62vh] w-full touch-none overflow-hidden rounded-2xl bg-slate-100 ring-1 ring-slate-200"
        style={{ cursor: tool === "pan" ? "grab" : tool === "select" ? "default" : "crosshair" }}
      >
        <div
          className="absolute left-0 top-0 origin-top-left"
          style={{ transform: `translate(${tx}px,${ty}px) scale(${scale})`, width, height }}
        >
          <img
            src={`${downloadUrl(jobId, "preprocessed")}?b=${bust}`}
            alt="original"
            className="absolute left-0 top-0 select-none"
            style={{ width, height, opacity }}
            draggable={false}
          />
          <canvas
            ref={canvasRef}
            className="absolute left-0 top-0"
            style={{ width, height, opacity: showLineart ? 1 : 0, mixBlendMode: "multiply" }}
          />
          {showRegions && (
            <svg
              className="pointer-events-none absolute left-0 top-0"
              width={width}
              height={height}
              viewBox={`0 0 ${width} ${height}`}
            >
              {regions.map((r) => (
                <path
                  key={r.id}
                  d={r.path}
                  fill={selected.includes(r.id) ? "rgba(124,58,237,0.28)" : "transparent"}
                  stroke={selected.includes(r.id) ? "#7c3aed" : "rgba(51,65,85,0.25)"}
                  strokeWidth={1}
                  vectorEffect="non-scaling-stroke"
                />
              ))}
              {regions.map((r) =>
                r.visible ? (
                  <text
                    key={`t${r.id}`}
                    x={r.label_pos.x}
                    y={r.label_pos.y}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fill="#1e293b"
                    fontWeight={600}
                    fontSize={Math.max(9, Math.min(28, Math.min(r.bbox[2], r.bbox[3]) * 0.3))}
                  >
                    {r.number ?? r.color_id}
                  </text>
                ) : null
              )}
            </svg>
          )}
        </div>
      </div>
      <p className="text-center text-xs text-slate-400">
        Fix Line / Erase paint the line art (then Apply) · Select + Merge/Delete/Split fix regions ·
        drag a number to move it · scroll to zoom · Pan to move
      </p>
    </div>
  );
}
