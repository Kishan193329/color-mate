import { motion } from "framer-motion";
import type { StepProgress } from "../api/client";

export default function Stepper({ steps }: { steps: StepProgress[] }) {
  return (
    <ol className="space-y-3">
      {steps.map((step) => (
        <li key={step.index} className="flex items-center gap-4">
          <div className="relative grid h-9 w-9 place-items-center">
            {step.status === "active" && (
              <motion.span
                className="absolute inset-0 rounded-full bg-forge-200"
                animate={{ scale: [1, 1.25, 1], opacity: [0.6, 0.2, 0.6] }}
                transition={{ repeat: Infinity, duration: 1.4 }}
              />
            )}
            <span
              className={`relative grid h-9 w-9 place-items-center rounded-full text-sm font-bold ${
                step.status === "done"
                  ? "bg-forge-600 text-white"
                  : step.status === "active"
                    ? "bg-forge-100 text-forge-700 ring-2 ring-forge-400"
                    : "bg-slate-100 text-slate-400"
              }`}
            >
              {step.status === "done" ? "✓" : step.index + 1}
            </span>
          </div>
          <span
            className={`text-sm font-medium ${
              step.status === "pending" ? "text-slate-400" : "text-slate-800"
            }`}
          >
            {step.name}
          </span>
        </li>
      ))}
    </ol>
  );
}
