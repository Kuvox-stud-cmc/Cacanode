"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { MessageSquare } from "lucide-react";
import { publicConfig } from "@/lib/public-config";
import type { WidgetSettings } from "@/lib/integrations-api";

type Props = {
  widget: WidgetSettings;
  token: string | null;
  iconPreviewUrl: string | null;
};

function widgetFrameUrl(): string | null {
  const configured = publicConfig.apiBaseUrl ?? publicConfig.widgetUrl;
  if (!configured || !/^https?:\/\//i.test(configured)) return null;
  return new URL("/widget/widget.html", configured).toString();
}

export default function InteractiveWidgetPreview({ widget, token, iconPreviewUrl }: Props) {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const readyRef = useRef(false);
  const previewConfigRef = useRef<object>({});
  const [open, setOpen] = useState(false);
  const [iconData, setIconData] = useState<{ source: string; dataUrl: string } | null>(null);
  const frameUrl = widgetFrameUrl();
  const frameOrigin = frameUrl ? new URL(frameUrl).origin : null;

  const previewConfig = useMemo(() => ({
    ...widget,
    iconUrl: iconData?.source === iconPreviewUrl ? iconData.dataUrl : null,
    hasCustomIcon: Boolean(widget.iconUrl),
    previewChatApiBaseUrl: publicConfig.apiBaseUrl ?? null,
  }), [iconData, iconPreviewUrl, widget]);

  useEffect(() => {
    previewConfigRef.current = previewConfig;
  }, [previewConfig]);

  useEffect(() => {
    if (!iconPreviewUrl) return;
    let cancelled = false;
    fetch(iconPreviewUrl)
      .then((response) => response.blob())
      .then((blob) => new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(blob);
      }))
      .then((dataUrl) => { if (!cancelled) setIconData({ source: iconPreviewUrl, dataUrl }); })
      .catch(() => { if (!cancelled) setIconData(null); });
    return () => { cancelled = true; };
  }, [iconPreviewUrl]);

  useEffect(() => {
    if (!frameOrigin || !token) return;
    const receive = (event: MessageEvent) => {
      if (event.source !== frameRef.current?.contentWindow || event.origin !== frameOrigin) return;
      if (!event.data || event.data.source !== "cacanode-widget") return;
      if (event.data.type === "ready") {
        readyRef.current = true;
        frameRef.current?.contentWindow?.postMessage({
          source: "cacanode-host",
          type: "init",
          token,
          previewConfig: previewConfigRef.current,
        }, frameOrigin);
      }
      if (event.data.type === "resize") setOpen(Boolean(event.data.open));
    };
    window.addEventListener("message", receive);
    return () => {
      readyRef.current = false;
      window.removeEventListener("message", receive);
    };
  }, [frameOrigin, token]);

  useEffect(() => {
    if (!readyRef.current || !frameOrigin) return;
    frameRef.current?.contentWindow?.postMessage({
      source: "cacanode-host",
      type: "preview-config",
      config: previewConfig,
    }, frameOrigin);
  }, [frameOrigin, previewConfig]);

  if (!frameUrl || !token) {
    const launcherStyle = {
      backgroundColor: widget.primaryColor,
      "--widget-launcher-color": widget.primaryColor,
    } as CSSProperties;
    const launcherClass = `widget-launcher-style widget-launcher-style--${(widget.iconStyle ?? "STANDARD").toLowerCase().replace("_", "-")}`;
    return (
      <div className="relative min-h-[640px] overflow-hidden rounded-lg border border-slate-200 bg-[radial-gradient(circle_at_top_left,_rgba(99,102,241,0.10),_transparent_38%),linear-gradient(to_bottom_right,_#f8fafc,_#eef2ff)]">
        <div className="space-y-1 p-5"><p className="text-sm font-medium text-slate-700">Customer website preview</p><p className="text-xs text-slate-500">Generate a widget token to enable live chat for this browser session.</p></div>
        <div className="mx-5 space-y-3 opacity-60" aria-hidden="true"><div className="h-3 w-2/5 rounded-full bg-slate-300" /><div className="h-2.5 rounded-full bg-slate-200" /><div className="h-2.5 w-4/5 rounded-full bg-slate-200" /><div className="grid grid-cols-2 gap-3 pt-3"><div className="h-20 rounded-lg border border-white bg-white/70" /><div className="h-20 rounded-lg border border-white bg-white/70" /></div></div>
        <button type="button" aria-label="Widget launcher preview"
          className={`${launcherClass} absolute bottom-5 ${widget.position === "BOTTOM_LEFT" ? "left-5" : "right-5"} grid size-14 place-items-center overflow-hidden rounded-full text-white`}
          style={launcherStyle}>
          {iconPreviewUrl ? <span className="size-full bg-cover bg-center" style={{ backgroundImage: `url(${iconPreviewUrl})` }} /> : <MessageSquare className="size-6" />}
        </button>
      </div>
    );
  }

  return (
    <div className="relative min-h-[640px] overflow-hidden rounded-lg border border-slate-200 bg-[radial-gradient(circle_at_top_left,_rgba(99,102,241,0.10),_transparent_38%),linear-gradient(to_bottom_right,_#f8fafc,_#eef2ff)]">
      <div className="space-y-1 p-5"><div className="flex items-center gap-2"><span className="size-2 rounded-full bg-emerald-500" /><p className="text-sm font-medium text-slate-700">Live widget preview</p></div><p className="text-xs text-slate-500">Open the widget and send a real test message. Preview activity is saved and counts toward quota.</p></div>
      <div className="mx-5 space-y-3 opacity-60" aria-hidden="true"><div className="h-3 w-2/5 rounded-full bg-slate-300" /><div className="h-2.5 rounded-full bg-slate-200" /><div className="h-2.5 w-4/5 rounded-full bg-slate-200" /><div className="grid grid-cols-2 gap-3 pt-3"><div className="h-20 rounded-lg border border-white bg-white/70" /><div className="h-20 rounded-lg border border-white bg-white/70" /></div></div>
      <iframe
        ref={frameRef}
        src={frameUrl}
        title="Interactive customer widget preview"
        className={`absolute bottom-3 border-0 bg-transparent transition-[width,height] duration-150 ${widget.position === "BOTTOM_LEFT" ? "left-3" : "right-3"}`}
        style={{ width: open ? "calc(100% - 24px)" : 72, height: open ? 600 : 72 }}
        allow="clipboard-write"
      />
    </div>
  );
}
