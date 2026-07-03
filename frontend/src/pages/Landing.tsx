import { Link } from "react-router-dom";
import { motion } from "framer-motion";

const features = [
  { icon: "✏️", title: "Clean Line Art", desc: "Turn any photo into crisp, printable outlines." },
  { icon: "🧩", title: "Smart Regions", desc: "Closed regions detected and ready to paint." },
  { icon: "🔢", title: "Auto Numbers", desc: "Every region numbered from a tidy palette." },
  { icon: "📦", title: "One-click Export", desc: "Line art, masks, SVG, and a game config in one zip." },
];

const flow = ["Upload Image", "Generate Line Art", "Detect Regions", "Edit", "Export"];

export default function Landing() {
  return (
    <div className="space-y-20">
      <section className="grid items-center gap-10 md:grid-cols-2">
        <div className="space-y-6">
          <span className="chip bg-forge-50 text-forge-700">
            AI Color-by-Number Studio
          </span>
          <h1 className="text-4xl font-extrabold leading-tight tracking-tight md:text-5xl">
            Turn any image into a
            <span className="text-forge-600"> color-by-number </span>
            masterpiece.
          </h1>
          <p className="max-w-md text-lg text-slate-600">
            ColorForge AI builds clean line art, detects paintable regions,
            places numbers, and exports everything — beautifully and simply.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link to="/upload" className="btn-primary">
              Start Creating →
            </Link>
            <Link to="/dashboard" className="btn-ghost">
              View Dashboard
            </Link>
          </div>
        </div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.4 }}
          className="card relative overflow-hidden"
        >
          <div className="grid grid-cols-6 gap-1">
            {Array.from({ length: 48 }).map((_, i) => (
              <motion.div
                key={i}
                className="aspect-square rounded-md"
                style={{
                  background: `hsl(${(i * 33) % 360} 70% ${70 - (i % 5) * 6}%)`,
                }}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: i * 0.012 }}
              />
            ))}
          </div>
          <p className="mt-4 text-center text-sm text-slate-500">
            Preview of a generated color map
          </p>
        </motion.div>
      </section>

      <section>
        <div className="mb-8 flex items-center justify-center gap-2 text-sm font-semibold text-slate-500">
          {flow.map((step, i) => (
            <div key={step} className="flex items-center gap-2">
              <span className="chip bg-white ring-1 ring-slate-200">{step}</span>
              {i < flow.length - 1 && <span className="text-forge-400">→</span>}
            </div>
          ))}
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <motion.div
              key={f.title}
              whileHover={{ y: -4 }}
              className="card"
            >
              <div className="text-3xl">{f.icon}</div>
              <h3 className="mt-3 font-bold">{f.title}</h3>
              <p className="mt-1 text-sm text-slate-500">{f.desc}</p>
            </motion.div>
          ))}
        </div>
      </section>
    </div>
  );
}
