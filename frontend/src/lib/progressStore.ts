// Local persistence for fill progress. Backend is the source of truth, but we
// mirror to localStorage so a refresh restores instantly (and works offline).

const key = (jobId: string) => `colorforge:progress:${jobId}`;

export function loadLocal(jobId: string): number[] {
  try {
    const raw = localStorage.getItem(key(jobId));
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((n) => typeof n === "number") : [];
  } catch {
    return [];
  }
}

export function saveLocal(jobId: string, filled: number[]): void {
  try {
    localStorage.setItem(key(jobId), JSON.stringify(filled));
  } catch {
    /* quota / disabled storage — ignore */
  }
}

export function clearLocal(jobId: string): void {
  try {
    localStorage.removeItem(key(jobId));
  } catch {
    /* ignore */
  }
}
