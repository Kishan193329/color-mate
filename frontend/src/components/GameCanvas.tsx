import { useRef, useState } from "react";
import { motion } from "framer-motion";
import type { GameConfig, Region } from "../api/client";

type Mode = "game" | "texture";

interface Props {
  config: GameConfig;
  mode: Mode;
  selectedNumber: number | null;
  filled: Set<number>;
  textureUrl: string;
  onFill: (regionId: number) => void;
}

const MIN_SCALE = 0.5;
const MAX_SCALE = 8;
const TEXTURE_ID = "cf-texture";

export default function GameCanvas({
  config,
  mode,
  selectedNumber,
  filled,
  textureUrl,
  onFill,
}: Props) {
  const { width, height, regions, palette } = config;
  const colorOf = (colorId: number) =>
    palette.find((p) => p.id === colorId)?.color ?? "#cccccc";

  // --- pan & zoom -----------------------------------------------------------
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  const [tx, setTx] = useState(0);
  const [ty, setTy] = useState(0);
  const panning = useRef(false);
  const last = useRef({ x: 0, y: 0 });
  const moved = useRef(false);

  const onPointerDown = (e: React.PointerEvent) => {
    panning.current = true;
    moved.current = false;
    last.current = { x: e.clientX, y: e.clientY };
    (e.target as Element).setPointerCapture?.(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!panning.current) return;
    const dx = e.clientX - last.current.x;
    const dy = e.clientY - last.current.y;
    if (Math.abs(dx) + Math.abs(dy) > 3) moved.current = true;
    setTx((v) => v + dx);
    setTy((v) => v + dy);
    last.current = { x: e.clientX, y: e.clientY };
  };
  const onPointerUp = () => {
    panning.current = false;
  };
  const onWheel = (e: React.WheelEvent) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const factor = e.deltaY < 0 ? 1.1 : 0.9;
    setScale((s) => {
      const ns = Math.min(MAX_SCALE, Math.max(MIN_SCALE, s * factor));
      const ratio = ns / s;
      setTx((x) => px - (px - x) * ratio);
      setTy((y) => py - (py - y) * ratio);
      return ns;
    });
  };
  const zoomBy = (factor: number) =>
    setScale((s) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s * factor)));
  const reset = () => {
    setScale(1);
    setTx(0);
    setTy(0);
  };

  // --- wrong-click shake ----------------------------------------------------
  const [shake, setShake] = useState<{ id: number; n: number } | null>(null);

  const handleClick = (region: Region) => {
    if (moved.current) return; // it was a pan, not a tap
    if (filled.has(region.id)) return;
    if (selectedNumber == null) return;
    if (region.color_id === selectedNumber) {
      onFill(region.id);
    } else {
      setShake({ id: region.id, n: (shake?.n ?? 0) + 1 });
    }
  };

  return (
    <div
      ref={containerRef}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerLeave={onPointerUp}
      onWheel={onWheel}
      className="relative h-[64vh] w-full touch-none overflow-hidden rounded-2xl bg-[radial-gradient(circle_at_1px_1px,#e2e8f0_1px,transparent_0)] [background-size:18px_18px] ring-1 ring-slate-200"
      style={{ cursor: panning.current ? "grabbing" : "grab" }}
    >
      <svg
        width="100%"
        height="100%"
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="xMidYMid meet"
      >
        <defs>
          <pattern
            id={TEXTURE_ID}
            patternUnits="userSpaceOnUse"
            width={width}
            height={height}
          >
            <image
              href={textureUrl}
              x={0}
              y={0}
              width={width}
              height={height}
              preserveAspectRatio="xMidYMid slice"
            />
          </pattern>
        </defs>

        <g transform={`translate(${tx} ${ty}) scale(${scale})`}>
          {regions.map((region) => {
            const isFilled = filled.has(region.id);
            const isHighlight =
              !isFilled &&
              selectedNumber != null &&
              region.color_id === selectedNumber;
            const fillColor =
              mode === "texture" ? `url(#${TEXTURE_ID})` : colorOf(region.color_id);
            const isShaking = shake?.id === region.id;

            return (
              <motion.g
                key={region.id}
                animate={isShaking ? { x: [0, -5, 5, -4, 4, 0] } : { x: 0 }}
                transition={{ duration: 0.4 }}
              >
                {/* Clickable base */}
                <path
                  d={region.path}
                  data-region-id={region.id}
                  data-color-id={region.color_id}
                  fill={isFilled && mode === "game" ? fillColor : "#fafafa"}
                  stroke="#334155"
                  strokeWidth={1}
                  vectorEffect="non-scaling-stroke"
                  style={{ cursor: "pointer" }}
                  onClick={() => handleClick(region)}
                />

                {/* Animated fill reveal (handles texture too) */}
                {isFilled && (
                  <motion.path
                    d={region.path}
                    fill={fillColor}
                    stroke="#334155"
                    strokeWidth={1}
                    vectorEffect="non-scaling-stroke"
                    pointerEvents="none"
                    initial={{ opacity: 0, scale: 0.97 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.35, ease: "easeOut" }}
                    style={{ transformOrigin: "center", transformBox: "fill-box" }}
                  />
                )}

                {/* Highlight pulse for the selected number */}
                {isHighlight && (
                  <motion.path
                    d={region.path}
                    fill={colorOf(region.color_id)}
                    pointerEvents="none"
                    animate={{ opacity: [0.18, 0.5, 0.18] }}
                    transition={{ repeat: Infinity, duration: 1.3 }}
                  />
                )}

                {/* Number label while unfilled */}
                {!isFilled && region.visible && (
                  <text
                    x={region.label_pos.x}
                    y={region.label_pos.y}
                    textAnchor="middle"
                    dominantBaseline="central"
                    pointerEvents="none"
                    fill={isHighlight ? "#1e293b" : "#64748b"}
                    fontWeight={600}
                    fontSize={Math.max(
                      9,
                      Math.min(42, Math.min(region.bbox[2], region.bbox[3]) * 0.32)
                    )}
                  >
                    {region.color_id}
                  </text>
                )}
              </motion.g>
            );
          })}
        </g>
      </svg>

      {/* Zoom controls */}
      <div className="absolute bottom-3 right-3 flex flex-col gap-1">
        <button className="zoombtn" onClick={() => zoomBy(1.2)} aria-label="Zoom in">
          ＋
        </button>
        <button className="zoombtn" onClick={() => zoomBy(0.8)} aria-label="Zoom out">
          －
        </button>
        <button className="zoombtn" onClick={reset} aria-label="Reset view">
          ⟲
        </button>
      </div>
    </div>
  );
}
