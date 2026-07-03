import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { downloadUrl, listJobs, type JobSummary } from "../api/client";

const STATUS_STYLES: Record<string, string> = {
  done: "bg-emerald-50 text-emerald-600",
  processing: "bg-amber-50 text-amber-600",
  created: "bg-slate-100 text-slate-500",
  error: "bg-red-50 text-red-600",
};

export default function Dashboard() {
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listJobs()
      .then(setJobs)
      .catch(() => setJobs([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight">Dashboard</h1>
          <p className="text-sm text-slate-500">All your ColorForge jobs.</p>
        </div>
        <Link to="/upload" className="btn-primary">
          + New artwork
        </Link>
      </div>

      {loading ? (
        <div className="text-center text-slate-400">Loading jobs…</div>
      ) : jobs.length === 0 ? (
        <div className="card text-center">
          <div className="text-4xl">🎨</div>
          <p className="mt-3 font-semibold">No jobs yet</p>
          <p className="text-sm text-slate-500">Upload an image to get started.</p>
          <Link to="/upload" className="btn-primary mt-4 inline-flex">
            Start now
          </Link>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {jobs.map((job) => (
            <motion.div key={job.job_id} whileHover={{ y: -4 }} className="card p-4">
              <div className="aspect-video overflow-hidden rounded-xl bg-slate-100">
                {job.preview_url ? (
                  <img
                    src={downloadUrl(job.job_id, "preview")}
                    alt={job.job_id}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <div className="grid h-full place-items-center text-slate-300">
                    no preview
                  </div>
                )}
              </div>
              <div className="mt-3 flex items-center justify-between">
                <span className="font-mono text-xs text-slate-500">
                  {job.job_id}
                </span>
                <span
                  className={`chip ${STATUS_STYLES[job.status] ?? STATUS_STYLES.created}`}
                >
                  {job.status}
                </span>
              </div>
              <div className="mt-3 flex gap-2">
                <Link
                  to={`/editor/${job.job_id}`}
                  className="btn-ghost flex-1 py-2 text-xs"
                >
                  Open
                </Link>
                <Link
                  to={`/export/${job.job_id}`}
                  className="btn-ghost flex-1 py-2 text-xs"
                >
                  Export
                </Link>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
