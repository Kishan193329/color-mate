import type { GameConfig } from "../api/client";

// Fine semantic label -> display color + coarse filter category.
export const LABEL_STYLE: Record<string, { color: string; category: string }> = {
  skin: { color: "#F2C49B", category: "Skin" },
  hair: { color: "#6B4A2B", category: "Hair" },
  fur: { color: "#A87C4F", category: "Hair" },
  clothing: { color: "#4F86C6", category: "Clothing" },
  background: { color: "#CBD5E1", category: "Background" },
  decoration: { color: "#C084FC", category: "Detail" },
  flower: { color: "#EC4899", category: "Detail" },
  detail: { color: "#94A3B8", category: "Detail" },
};

export const FILTERS = ["Skin", "Hair", "Clothing", "Background", "Detail"] as const;

const styleFor = (label?: string | null) =>
  (label && LABEL_STYLE[label]) || { color: "#E2E8F0", category: "Detail" };

interface Props {
  config: GameConfig;
  active: Set<string>; // active filter categories; empty = show all
}

export default function SemanticCanvas({ config, active }: Props) {
  const { width, height, regions } = config;
  const showAll = active.size === 0;

  return (
    <div className="h-[64vh] w-full overflow-hidden rounded-2xl bg-slate-50 ring-1 ring-slate-200">
      <svg
        width="100%"
        height="100%"
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="xMidYMid meet"
      >
        {regions.map((r) => {
          const st = styleFor(r.semantic_label);
          const on = showAll || active.has(st.category);
          return (
            <g key={r.id} opacity={on ? 1 : 0.1}>
              <path
                d={r.path}
                fill={st.color}
                stroke="#334155"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
              />
              {r.visible && on && (
                <text
                  x={r.label_pos.x}
                  y={r.label_pos.y}
                  textAnchor="middle"
                  dominantBaseline="central"
                  pointerEvents="none"
                  fill="#1e293b"
                  fontWeight={600}
                  fontSize={Math.max(
                    9,
                    Math.min(28, Math.min(r.bbox[2], r.bbox[3]) * 0.18)
                  )}
                >
                  {r.semantic_label ?? ""}
                </text>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
