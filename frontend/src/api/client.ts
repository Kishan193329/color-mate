// Typed API client for the ColorForge backend.
// During dev, Vite proxies /api -> http://localhost:8000 (see vite.config.ts).

export type JobStatus = "created" | "processing" | "done" | "error";

export interface StepProgress {
  name: string;
  status: "pending" | "active" | "done";
  index: number;
}

export interface JobResponse {
  job_id: string;
  status: JobStatus;
  progress: number;
  current_step: string | null;
  steps: StepProgress[];
  error: string | null;
  created_at: string;
  updated_at: string;
  artifacts: Record<string, string>;
}

export interface JobSummary {
  job_id: string;
  status: JobStatus;
  progress: number;
  created_at: string;
  preview_url: string | null;
}

export interface UploadResponse {
  job_id: string;
  status: JobStatus;
  original_url: string;
}

const BASE = "/api";

async function asJson<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail ?? detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail);
  }
  return res.json() as Promise<T>;
}

export async function uploadImage(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${BASE}/upload`, { method: "POST", body: form });
  return asJson<UploadResponse>(res);
}

export async function startProcessing(jobId: string): Promise<JobResponse> {
  const res = await fetch(`${BASE}/process`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ job_id: jobId }),
  });
  return asJson<JobResponse>(res);
}

export async function getJob(jobId: string): Promise<JobResponse> {
  const res = await fetch(`${BASE}/job/${jobId}`);
  return asJson<JobResponse>(res);
}

export async function listJobs(): Promise<JobSummary[]> {
  const res = await fetch(`${BASE}/jobs`);
  const body = await asJson<{ jobs: JobSummary[] }>(res);
  return body.jobs;
}

// Build a download URL for an artifact logical name.
export function downloadUrl(jobId: string, fileType: string): string {
  return `${BASE}/download/${jobId}/${fileType}`;
}

// ---------------------------------------------------------------------------
// Phase 4 — interactive game
// ---------------------------------------------------------------------------

export interface Region {
  id: number;
  path: string;
  area: number;
  bbox: [number, number, number, number];
  label_pos: { x: number; y: number };
  sampled_color?: string;
  color_id: number;
  number?: number;
  visible: boolean;
  filled?: boolean;
  // Phase 7 — semantic helper layer (may be null when disabled).
  semantic_label?: string | null;
  parent_object?: string | null;
  group_id?: number | null;
  region_type?: string | null;
}

export interface PaletteEntry {
  id: number;
  number: number;
  color: string;
}

export interface GameConfig {
  version: number;
  job_id: string;
  width: number;
  height: number;
  fill_mode?: "texture" | "palette";
  regions: Region[];
  palette: PaletteEntry[];
  stats?: Record<string, number>;
  difficulty?: string;
  assets?: Record<string, string>;
}

export interface ProgressDoc {
  job_id: string;
  filled: number[];
  total: number;
  percent: number;
  updated_at: string | null;
}

export async function getGameConfig(jobId: string): Promise<GameConfig> {
  const res = await fetch(`${BASE}/job/${jobId}/game-config`);
  return asJson<GameConfig>(res);
}

export async function getProgress(jobId: string): Promise<ProgressDoc> {
  const res = await fetch(`${BASE}/job/${jobId}/progress`);
  return asJson<ProgressDoc>(res);
}

export async function saveProgress(
  jobId: string,
  filled: number[]
): Promise<ProgressDoc> {
  const res = await fetch(`${BASE}/job/${jobId}/save-progress`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ filled }),
  });
  return asJson<ProgressDoc>(res);
}

// ---------------------------------------------------------------------------
// Phase 5 — quality cleanup & difficulty
// ---------------------------------------------------------------------------

export interface QualityReport {
  version: number;
  profile: string;
  score: number;
  regions_count: number;
  tiny_regions_removed: number;
  tiny_regions_merged: number;
  gaps_repaired: number;
  dust_removed: number;
  line_fragments: number;
  avg_region_area: number;
  small_regions_remaining: number;
  notes: string[];
}

export type CleanupAction =
  | "auto-clean"
  | "make-easier"
  | "make-detailed"
  | "undo-cleanup";

export async function getQualityReport(jobId: string): Promise<QualityReport> {
  const res = await fetch(`${BASE}/job/${jobId}/quality-report`);
  return asJson<QualityReport>(res);
}

export async function runCleanup(
  jobId: string,
  action: CleanupAction
): Promise<QualityReport> {
  const res = await fetch(`${BASE}/job/${jobId}/${action}`, { method: "POST" });
  return asJson<QualityReport>(res);
}

// ---------------------------------------------------------------------------
// Phase 7 — semantic grouping
// ---------------------------------------------------------------------------

export interface SemanticSummary {
  mode: string;
  group_count: number;
  objects: { name: string; region_count: number }[];
  labels: Record<string, number>;
}

export async function smartGroup(jobId: string): Promise<SemanticSummary> {
  const res = await fetch(`${BASE}/job/${jobId}/smart-group`, { method: "POST" });
  return asJson<SemanticSummary>(res);
}

// ---------------------------------------------------------------------------
// Phase 8 — AI assist (RunPod) settings
// ---------------------------------------------------------------------------

export interface AiStatus {
  mode: "local" | "runpod";
  env_mode: string;
  runpod_url_configured: boolean;
  runpod_url: string;
  timeout_seconds: number;
  health: "local" | "ready" | "offline";
  last_source: "ai" | "fallback" | "local" | null;
  last_error: string | null;
}

export interface AiTestResult {
  reachable: boolean;
  latency_ms?: number;
  detail: unknown;
}

export async function getAiStatus(): Promise<AiStatus> {
  return asJson<AiStatus>(await fetch(`${BASE}/ai/status`));
}

export async function testAiConnection(): Promise<AiTestResult> {
  return asJson<AiTestResult>(await fetch(`${BASE}/ai/test`, { method: "POST" }));
}

export async function setAiMode(mode: "local" | "runpod"): Promise<AiStatus> {
  return asJson<AiStatus>(
    await fetch(`${BASE}/ai/mode`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode }),
    })
  );
}

// ---------------------------------------------------------------------------
// Phase 9 — advanced line art
// ---------------------------------------------------------------------------

export interface LineartSettings {
  mode?: string;
  thickness?: number;
  detail?: number;
  gap?: number;
  remove_noise?: boolean;
}

export interface LineartQuality {
  score: number;
  line_density: number;
  fragments: number;
  isolated_dots: number;
  settings?: LineartSettings;
  modes?: { id: string; label: string }[];
}

export async function getLineartQuality(jobId: string): Promise<LineartQuality> {
  return asJson<LineartQuality>(await fetch(`${BASE}/job/${jobId}/lineart-quality`));
}

export async function previewLineart(
  jobId: string,
  overrides: LineartSettings
): Promise<{ preview_url: string; quality: LineartQuality }> {
  return asJson(
    await fetch(`${BASE}/job/${jobId}/regenerate-lineart`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...overrides, preview_only: true }),
    })
  );
}

export async function regenerateLineart(
  jobId: string,
  overrides: LineartSettings
): Promise<{ quality: LineartQuality; settings: LineartSettings }> {
  return asJson(
    await fetch(`${BASE}/job/${jobId}/regenerate-lineart`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(overrides),
    })
  );
}

// ---------------------------------------------------------------------------
// Phase 10 — manual correction tools (versioned)
// ---------------------------------------------------------------------------

export interface VersionState {
  versions: string[];
  labels: string[];
  current: number;
  can_undo: boolean;
  can_redo: boolean;
}

export interface EditResult {
  ok: boolean;
  version: string;
  versions: VersionState;
}

async function postJson<T>(path: string, body?: unknown): Promise<T> {
  return asJson<T>(
    await fetch(`${BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  );
}

export async function saveLineart(jobId: string, blob: Blob): Promise<EditResult> {
  const form = new FormData();
  form.append("file", blob, "lineart.png");
  return asJson<EditResult>(
    await fetch(`${BASE}/job/${jobId}/lineart/save`, { method: "POST", body: form })
  );
}

export const recalcRegions = (jobId: string) =>
  postJson<EditResult>(`/job/${jobId}/lineart/recalculate-regions`);
export const mergeRegions = (jobId: string, region_ids: number[]) =>
  postJson<EditResult>(`/job/${jobId}/regions/merge`, { region_ids });
export const splitRegion = (jobId: string, region_id: number, points: number[][]) =>
  postJson<EditResult>(`/job/${jobId}/regions/split`, { region_id, points });
export const deleteRegions = (
  jobId: string,
  opts: { region_ids?: number[]; min_area?: number }
) => postJson<EditResult>(`/job/${jobId}/regions/delete`, opts);
export const updateRegionLabel = (
  jobId: string,
  region_id: number,
  patch: { label_pos?: { x: number; y: number }; visible?: boolean }
) => postJson<EditResult>(`/job/${jobId}/regions/update-label`, { region_id, ...patch });
export const updateRegionColor = (jobId: string, region_id: number, color_id: number) =>
  postJson<EditResult>(`/job/${jobId}/regions/update-color`, { region_id, color_id });
export async function getVersions(jobId: string): Promise<VersionState> {
  return asJson<VersionState>(await fetch(`${BASE}/job/${jobId}/versions`));
}
export const undoEdit = (jobId: string) => postJson<EditResult>(`/job/${jobId}/undo`);
export const redoEdit = (jobId: string) => postJson<EditResult>(`/job/${jobId}/redo`);
