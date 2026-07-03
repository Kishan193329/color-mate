import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { motion } from "framer-motion";

const navItems = [
  { to: "/upload", label: "Create" },
  { to: "/dashboard", label: "Dashboard" },
  { to: "/ai", label: "AI" },
];

export default function Layout() {
  const location = useLocation();
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-30 border-b border-slate-100 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link to="/" className="flex items-center gap-2">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-forge-600 text-white shadow-glow">
              ◆
            </span>
            <span className="text-lg font-extrabold tracking-tight">
              ColorForge <span className="text-forge-600">AI</span>
            </span>
          </Link>
          <nav className="flex items-center gap-1">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-lg px-4 py-2 text-sm font-semibold transition ${
                    isActive
                      ? "bg-forge-50 text-forge-700"
                      : "text-slate-600 hover:bg-slate-50"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <motion.main
        key={location.pathname}
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="mx-auto w-full max-w-6xl flex-1 px-6 py-10"
      >
        <Outlet />
      </motion.main>

      <footer className="border-t border-slate-100 py-6 text-center text-xs text-slate-400">
        ColorForge AI · Sprint 1 · Line Art → Regions → Numbers → Export
      </footer>
    </div>
  );
}
