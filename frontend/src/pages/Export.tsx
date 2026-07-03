import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl, getJob, type JobResponse } from "../api/client";

const FILES: { type: string; label: string; ext: string; desc: string }[] = [
  { type: "lineart", label: "Line Art", ext: "PNG", desc: "Clean black-on-white outlines." },
  { type: "region_mask", label: "Region Mask", ext: "PNG", desc: "Solved color map of every region." },
  { type: "outline", label: "Outline", ext: "SVG", desc: "Vector boundaries with numbers." },
  { type: "preview", label: "Preview", ext: "PNG", desc: "Numbered color-by-number sheet." },
  { type: "regions", label: "Regions", ext: "JSON", desc: "Region geometry, color, number." },
  { type: "palette", label: "Palette", ext: "JSON", desc: "Ordered color palette." },
  { type: "game_config", label: "Game Config", ext: "JSON", desc: "Metadata for the playable game." },
];

export default function Export() {
  const { jobId } = useParams<{ jobId: string }>();
  const [job, setJob] = useState<JobResponse | null>(null);

  useEffect(() => {
    if (jobId) getJob(jobId).then(setJob).catch(() => {});
  }, [jobId]);

  if (!jobId) return null;
  const available = job?.artifacts ?? {};

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-extrabold tracking-tight">Export</h1>
        <p className="mt-2 text-slate-500">
          Download individual assets or grab the full bundle.
        </p>
      </div>

      <div className="card flex flex-col items-center gap-4 bg-forge-50 text-center">
        <div className="text-4xl">📦</div>
        <div>
          <h2 className="text-lg font-bold">Complete Bundle</h2>
          <p className="text-sm text-slate-500">
            Every artifact in a single zip — ready to import.
          </p>
        </div>
        <a className="btn-primary" href={downloadUrl(jobId, "result_zip")}>
          ⬇ Download result.zip
        </a>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {FILES.map((f) => {
          const ready = Boolean(available[f.type]);
          return (
            <div key={f.type} className="card flex items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-bold">{f.label}</h3>
                  <span className="chip bg-slate-100 text-slate-500">{f.ext}</span>
                </div>
                <p className="text-sm text-slate-500">{f.desc}</p>
              </div>
              <a
                className={`btn ${ready ? "btn-ghost" : "btn-ghost opacity-40 pointer-events-none"}`}
                href={downloadUrl(jobId, f.type)}
              >
                ⬇
              </a>
            </div>
          );
        })}
      </div>

      <div className="flex justify-center gap-3">
        <Link to={`/editor/${jobId}`} className="btn-ghost">
          ← Back to editor
        </Link>
        <Link to="/upload" className="btn-primary">
          Create another →
        </Link>
      </div>
    </div>
  );
}
