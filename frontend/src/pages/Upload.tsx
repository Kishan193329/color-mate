import { useCallback, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { uploadImage, startProcessing } from "../api/client";

export default function Upload() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const select = useCallback((f: File | null) => {
    setError(null);
    if (!f) return;
    if (!f.type.startsWith("image/")) {
      setError("Please choose an image file (PNG, JPG, or WebP).");
      return;
    }
    setFile(f);
    setPreview(URL.createObjectURL(f));
  }, []);

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    select(e.dataTransfer.files?.[0] ?? null);
  };

  const handleGenerate = async () => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const { job_id } = await uploadImage(file);
      await startProcessing(job_id);
      navigate(`/processing/${job_id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-extrabold tracking-tight">Upload an image</h1>
        <p className="mt-2 text-slate-500">
          We&apos;ll generate line art, detect regions, and place numbers.
        </p>
      </div>

      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        className={`card cursor-pointer border-2 border-dashed text-center transition ${
          dragging ? "border-forge-400 bg-forge-50" : "border-slate-200"
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          className="hidden"
          onChange={(e) => select(e.target.files?.[0] ?? null)}
        />
        {preview ? (
          <motion.img
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            src={preview}
            alt="preview"
            className="mx-auto max-h-72 rounded-xl object-contain"
          />
        ) : (
          <div className="py-12">
            <div className="text-4xl">🖼️</div>
            <p className="mt-3 font-semibold">Drop your image here</p>
            <p className="text-sm text-slate-400">or click to browse · PNG, JPG, WebP</p>
          </div>
        )}
      </div>

      {file && (
        <p className="text-center text-sm text-slate-500">
          Selected: <span className="font-medium text-slate-700">{file.name}</span>
        </p>
      )}
      {error && (
        <p className="rounded-lg bg-red-50 px-4 py-3 text-center text-sm text-red-600">
          {error}
        </p>
      )}

      <div className="flex justify-center gap-3">
        <button
          className="btn-primary"
          disabled={!file || busy}
          onClick={handleGenerate}
        >
          {busy ? "Uploading…" : "Generate Line Art →"}
        </button>
      </div>
    </div>
  );
}
