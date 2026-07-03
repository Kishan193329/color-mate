import { Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import Landing from "./pages/Landing";
import Upload from "./pages/Upload";
import Processing from "./pages/Processing";
import Editor from "./pages/Editor";
import Export from "./pages/Export";
import Dashboard from "./pages/Dashboard";
import AiSettings from "./pages/AiSettings";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Landing />} />
        <Route path="/upload" element={<Upload />} />
        <Route path="/processing/:jobId" element={<Processing />} />
        <Route path="/editor/:jobId" element={<Editor />} />
        <Route path="/export/:jobId" element={<Export />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/ai" element={<AiSettings />} />
      </Route>
    </Routes>
  );
}
