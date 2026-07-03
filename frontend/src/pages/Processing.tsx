import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import Stepper from "../components/Stepper";
import { getJob, type JobResponse } from "../api/client";

export default function Processing() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const [job, setJob] = useState<JobResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!jobId) return;
    let active = true;

    const poll = async () => {
      try {
        const data = await getJob(jobId);
        if (!active) return;
        setJob(data);
        if (data.status === "done") {
          setTimeout(() => navigate(`/editor/${jobId}`), 700);
          return;
        }
        if (data.status === "error") {
          setError(data.error ?? "Processing failed");
          return;
        }
        setTimeout(poll, 700);
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : "Lost connection");
      }
    };
    poll();
    return () => {
      active = false;
    };
  }, [jobId, navigate]);

  const progress = job?.progress ?? 0;

  return (
    <div className="mx-auto max-w-xl space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-extrabold tracking-tight">Forging your artwork</h1>
        <p className="mt-2 text-slate-500">
          {job?.current_step ?? "Starting up…"}
        </p>
      </div>

      <div className="card space-y-6">
        <div>
          <div className="mb-2 flex justify-between text-sm font-medium text-slate-500">
            <span>Progress</span>
            <span>{progress}%</span>
          </div>
          <div className="h-3 overflow-hidden rounded-full bg-slate-100">
            <motion.div
              className="h-full rounded-full bg-forge-600"
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.4 }}
            />
          </div>
        </div>

        {job && <Stepper steps={job.steps} />}

        {error && (
          <div className="space-y-3 rounded-lg bg-red-50 p-4 text-sm text-red-600">
            <p>{error}</p>
            <button className="btn-ghost" onClick={() => navigate("/upload")}>
              Try another image
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
