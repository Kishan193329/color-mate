import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  getAiStatus,
  setAiMode,
  testAiConnection,
  type AiStatus,
  type AiTestResult,
} from "../api/client";

const STATUS_BADGE: Record<string, { label: string; cls: string }> = {
  ready: { label: "Ready", cls: "bg-emerald-50 text-emerald-600" },
  local: { label: "Local", cls: "bg-slate-100 text-slate-500" },
  offline: { label: "Offline", cls: "bg-red-50 text-red-600" },
};

export default function AiSettings() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [test, setTest] = useState<AiTestResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = async () => {
    try {
      setStatus(await getAiStatus());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load AI status");
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const choose = async (mode: "local" | "runpod") => {
    setBusy(true);
    setError(null);
    try {
      setStatus(await setAiMode(mode));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change mode");
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setBusy(true);
    setTest(null);
    try {
      setTest(await testAiConnection());
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  // Effective status badge. "Fallback" only matters while RunPod is selected
  // and the last run fell back to local.
  const badgeKey =
    status?.mode === "runpod" && status?.last_source === "fallback"
      ? "fallback"
      : status?.health ?? "local";
  const badge =
    badgeKey === "fallback"
      ? { label: "Fallback", cls: "bg-amber-50 text-amber-600" }
      : STATUS_BADGE[badgeKey] ?? STATUS_BADGE.local;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight">AI Assist</h1>
        <p className="text-sm text-slate-500">
          Optional RunPod acceleration for heavy AI tasks.
        </p>
      </div>

      <div className="card flex items-start gap-3 bg-forge-50">
        <span className="text-xl">💡</span>
        <p className="text-sm text-forge-800">
          AI Assist improves line art and object understanding, but region
          detection stays local for accuracy.
        </p>
      </div>

      {/* Engine choice */}
      <div className="grid gap-3 sm:grid-cols-2">
        <EngineCard
          title="Local Engine"
          desc="On-box OpenCV pipeline. Always available, fully deterministic."
          active={status?.mode === "local"}
          onClick={() => choose("local")}
          disabled={busy}
        />
        <EngineCard
          title="RunPod AI Assist"
          desc="Sends the image to a RunPod worker for enhancement, line art & semantics."
          active={status?.mode === "runpod"}
          onClick={() => choose("runpod")}
          disabled={busy}
        />
      </div>

      {/* Status */}
      <div className="card space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold uppercase tracking-wide text-slate-400">
            Status
          </h3>
          <motion.span
            key={badge.label}
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className={`chip ${badge.cls}`}
          >
            {badge.label}
          </motion.span>
        </div>
        <dl className="grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-slate-400">Mode</dt>
          <dd className="font-medium">{status?.mode ?? "—"}</dd>
          <dt className="text-slate-400">RunPod URL</dt>
          <dd className="truncate font-mono text-xs text-slate-600">
            {status?.runpod_url || "(not set)"}
          </dd>
          <dt className="text-slate-400">Last run</dt>
          <dd className="font-medium">{status?.last_source ?? "—"}</dd>
          <dt className="text-slate-400">Timeout</dt>
          <dd className="font-medium">{status?.timeout_seconds ?? "—"}s</dd>
        </dl>

        <div className="flex items-center gap-3">
          <button className="btn-ghost py-2" disabled={busy} onClick={runTest}>
            {busy ? "Testing…" : "Test AI Connection"}
          </button>
          {test && (
            <span className={`text-sm ${test.reachable ? "text-emerald-600" : "text-red-600"}`}>
              {test.reachable
                ? `Reachable (${test.latency_ms ?? "?"} ms)`
                : "Unreachable"}
            </span>
          )}
        </div>
        {status?.last_error && (
          <p className="text-xs text-amber-600">Last error: {status.last_error}</p>
        )}
      </div>

      {error && (
        <p className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
      )}

      <p className="text-xs text-slate-400">
        Tip: set <code>RUNPOD_API_URL</code> and run the worker in{" "}
        <code>runpod-worker/</code>. If RunPod is unreachable, processing
        automatically falls back to the local engine.
      </p>
    </div>
  );
}

function EngineCard({
  title,
  desc,
  active,
  onClick,
  disabled,
}: {
  title: string;
  desc: string;
  active?: boolean;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`card text-left transition ${
        active ? "ring-2 ring-forge-500" : "hover:bg-slate-50"
      }`}
    >
      <div className="flex items-center justify-between">
        <h3 className="font-bold">{title}</h3>
        <span
          className={`grid h-5 w-5 place-items-center rounded-full text-xs ${
            active ? "bg-forge-600 text-white" : "ring-1 ring-slate-300"
          }`}
        >
          {active ? "✓" : ""}
        </span>
      </div>
      <p className="mt-1 text-sm text-slate-500">{desc}</p>
    </button>
  );
}
