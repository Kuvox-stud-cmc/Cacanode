"use client";

import { useState, useRef } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Bot, Upload, Check, Copy, CloudUpload, ArrowRight, ArrowLeft } from "lucide-react";
import ChatWidgetPanel from "@/components/widget/ChatWidgetPanel";

const EMBED_CODE = `<script
  src="https://cdn.cacanode.com/widget.js"
  data-tenant="tenant_demo123"
  data-color="#4f46e5"
  defer
></script>`;

const COLOR_PRESETS = [
  { color: "#4f46e5", label: "Indigo" },
  { color: "#7c3aed", label: "Violet" },
  { color: "#059669", label: "Emerald" },
  { color: "#dc2626", label: "Rose" },
  { color: "#d97706", label: "Amber" },
  { color: "#1e293b", label: "Slate" },
];

export default function OnboardingPage() {
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadDone, setUploadDone] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [droppedFile, setDroppedFile] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [botName, setBotName] = useState("Support Bot");
  const [welcomeMsg, setWelcomeMsg] = useState("Hello! How can I help you today?");
  const [primaryColor, setPrimaryColor] = useState("#4f46e5");
  const [position, setPosition] = useState<"bottom-right" | "bottom-left">("bottom-right");
  const [copied, setCopied] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFile = (name: string) => {
    setDroppedFile(name);
    setUploading(true);
    setUploadProgress(0);
    let progress = 0;
    const iv = setInterval(() => {
      progress += 4;
      setUploadProgress(Math.min(progress, 100));
      if (progress >= 100) {
        clearInterval(iv);
        setUploading(false);
        setUploadDone(true);
      }
    }, 80);
  };

  const copyEmbed = async () => {
    await navigator.clipboard.writeText(EMBED_CODE);
    setCopied(true);
    toast.success("Snippet copied!");
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-slate-200 px-6 py-3 flex items-center gap-3">
        <div className="w-7 h-7 bg-indigo-600 rounded-md flex items-center justify-center">
          <Bot className="w-4 h-4 text-white" />
        </div>
        <span className="font-bold text-slate-800">CacaNode</span>
      </header>

      <div className="flex-1 max-w-2xl mx-auto w-full px-4 py-10">
        {/* Progress */}
        <div className="mb-8">
          <div className="flex items-center justify-between text-xs font-medium mb-3">
            {["Upload docs", "Customize widget", "Go live"].map((label, i) => (
              <div
                key={label}
                className={`flex items-center gap-1.5 ${
                  i + 1 < step ? "text-indigo-600" : i + 1 === step ? "text-slate-800" : "text-slate-400"
                }`}
              >
                <div
                  className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                    i + 1 < step
                      ? "bg-indigo-600 text-white"
                      : i + 1 === step
                      ? "bg-indigo-100 text-indigo-700 ring-2 ring-indigo-500"
                      : "bg-slate-200 text-slate-500"
                  }`}
                >
                  {i + 1 < step ? <Check className="w-3 h-3" /> : i + 1}
                </div>
                <span className="hidden sm:inline">{label}</span>
              </div>
            ))}
          </div>
          <Progress value={(step / 3) * 100} className="h-1.5" />
        </div>

        {/* Step 1 — Upload */}
        {step === 1 && (
          <div className="space-y-6">
            <div>
              <h1 className="text-2xl font-bold text-slate-900 mb-1">Upload your first document</h1>
              <p className="text-slate-500">Your AI bot will learn from this content and answer visitor questions.</p>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.docx,.txt"
              className="hidden"
              onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0].name)}
            />

            {!droppedFile ? (
              <div
                onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsDragging(false);
                  const f = e.dataTransfer.files[0];
                  if (f) handleFile(f.name);
                }}
                onClick={() => fileInputRef.current?.click()}
                className={`border-2 border-dashed rounded-2xl p-12 text-center cursor-pointer transition-colors ${
                  isDragging ? "border-indigo-500 bg-indigo-50" : "border-slate-300 bg-white hover:border-indigo-400"
                }`}
              >
                <CloudUpload className="w-12 h-12 text-slate-400 mx-auto mb-4" />
                <p className="font-medium text-slate-700 mb-1">Drop your file here, or click to browse</p>
                <p className="text-sm text-slate-400">PDF, DOCX, TXT — up to 50 MB</p>
              </div>
            ) : (
              <div className="bg-white border border-slate-200 rounded-2xl p-6">
                <div className="flex items-center gap-3 mb-4">
                  <Upload className="w-5 h-5 text-indigo-600" />
                  <span className="text-sm font-medium text-slate-700 truncate">{droppedFile}</span>
                  {uploadDone && (
                    <span className="ml-auto shrink-0 flex items-center gap-1 text-green-600 text-xs font-medium">
                      <Check className="w-3.5 h-3.5" /> Ready
                    </span>
                  )}
                </div>
                {!uploadDone && (
                  <div>
                    <Progress value={uploadProgress} className="h-1.5 mb-1" />
                    <p className="text-xs text-slate-400">{uploading ? "Processing..." : "Done"} {uploadProgress}%</p>
                  </div>
                )}
                {uploadDone && (
                  <div className="bg-green-50 rounded-lg px-3 py-2 text-xs text-green-700 font-medium flex items-center gap-2">
                    <Check className="w-3.5 h-3.5" />
                    Document processed and ready for your bot!
                  </div>
                )}
              </div>
            )}

            <div className="flex justify-between">
              <Button variant="ghost" className="text-slate-500" onClick={() => router.push("/dashboard")}>
                Skip for now
              </Button>
              <Button
                className="bg-indigo-600 hover:bg-indigo-700 text-white gap-2"
                disabled={!uploadDone}
                onClick={() => setStep(2)}
              >
                Continue <ArrowRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        )}

        {/* Step 2 — Customize */}
        {step === 2 && (
          <div className="space-y-6">
            <div>
              <h1 className="text-2xl font-bold text-slate-900 mb-1">Customize your widget</h1>
              <p className="text-slate-500">Make it feel like yours. Changes preview in real time.</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <Label>Bot name</Label>
                  <Input
                    value={botName}
                    onChange={(e) => setBotName(e.target.value)}
                    maxLength={30}
                    placeholder="Support Bot"
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Welcome message</Label>
                  <textarea
                    value={welcomeMsg}
                    onChange={(e) => setWelcomeMsg(e.target.value)}
                    maxLength={120}
                    rows={2}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none"
                    placeholder="Hello! How can I help you?"
                  />
                </div>

                <div className="space-y-2">
                  <Label>Brand color</Label>
                  <div className="flex flex-wrap gap-2">
                    {COLOR_PRESETS.map(({ color, label }) => (
                      <button
                        key={color}
                        title={label}
                        onClick={() => setPrimaryColor(color)}
                        className={`w-7 h-7 rounded-full border-2 transition-all ${
                          primaryColor === color ? "border-slate-900 scale-110" : "border-transparent"
                        }`}
                        style={{ backgroundColor: color }}
                      />
                    ))}
                    <input
                      type="color"
                      value={primaryColor}
                      onChange={(e) => setPrimaryColor(e.target.value)}
                      className="w-7 h-7 rounded-full border-2 border-slate-200 cursor-pointer"
                      title="Custom color"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label>Position</Label>
                  <div className="flex gap-2">
                    {(["bottom-right", "bottom-left"] as const).map((pos) => (
                      <button
                        key={pos}
                        onClick={() => setPosition(pos)}
                        className={`px-3 py-1.5 rounded-md text-xs border transition-colors ${
                          position === pos
                            ? "bg-indigo-600 text-white border-indigo-600"
                            : "bg-white text-slate-600 border-slate-200 hover:border-indigo-400"
                        }`}
                      >
                        {pos === "bottom-right" ? "Bottom Right" : "Bottom Left"}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Live preview */}
              <div className="flex items-center justify-center">
                <div className="scale-90 origin-top">
                  <ChatWidgetPanel
                    alwaysOpen
                    primaryColor={primaryColor}
                    botName={botName || "Support Bot"}
                  />
                </div>
              </div>
            </div>

            <div className="flex justify-between">
              <Button variant="outline" className="gap-2" onClick={() => setStep(1)}>
                <ArrowLeft className="w-4 h-4" /> Back
              </Button>
              <Button
                className="bg-indigo-600 hover:bg-indigo-700 text-white gap-2"
                onClick={() => setStep(3)}
              >
                Continue <ArrowRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        )}

        {/* Step 3 — Go live */}
        {step === 3 && (
          <div className="space-y-6">
            <div className="text-center py-4">
              <div className="w-16 h-16 bg-indigo-600 rounded-full flex items-center justify-center mx-auto mb-4 shadow-lg shadow-indigo-200">
                <Check className="w-8 h-8 text-white" />
              </div>
              <h1 className="text-2xl font-bold text-slate-900 mb-2">You&apos;re all set!</h1>
              <p className="text-slate-500">
                Paste this snippet into your website and your AI support bot goes live instantly.
              </p>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-5">
              <Label className="text-xs text-slate-500 mb-2 block">Add before &lt;/body&gt;</Label>
              <div className="relative">
                <pre className="bg-slate-900 text-slate-100 rounded-lg p-4 text-xs overflow-x-auto whitespace-pre-wrap leading-relaxed">
                  {EMBED_CODE}
                </pre>
                <button
                  onClick={copyEmbed}
                  className="absolute top-3 right-3 p-1.5 rounded bg-slate-700 hover:bg-slate-600 text-slate-200 transition-colors"
                >
                  {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <Button
                onClick={copyEmbed}
                variant="outline"
                className="gap-2 w-full"
              >
                <Copy className="w-4 h-4" />
                Copy snippet
              </Button>
              <Button
                className="bg-indigo-600 hover:bg-indigo-700 text-white gap-2 w-full"
                onClick={() => router.push("/dashboard")}
              >
                Go to dashboard <ArrowRight className="w-4 h-4" />
              </Button>
            </div>

            <div className="flex justify-start">
              <Button variant="ghost" className="gap-2 text-slate-500" onClick={() => setStep(2)}>
                <ArrowLeft className="w-4 h-4" /> Back
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
