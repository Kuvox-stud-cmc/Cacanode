"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Copy,
  Check,
  MessageSquare,
  Eye,
  EyeOff,
  RefreshCw,
  ExternalLink,
} from "lucide-react";

const EMBED_CODE = `<script>window.CacaNodeConfig={tenantKey:"demo-key"}</script>
<script src="https://api.cacanode.com/widget/widget.js"></script>`;

const MOCK_API_KEY = "sk-ccn-a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";

export default function SettingsPage() {
  const [displayName, setDisplayName] = useState("Support Bot");
  const [welcomeMessage, setWelcomeMessage] = useState("Hello! How can I help you today?");
  const [primaryColor, setPrimaryColor] = useState("#4f46e5");
  const [position, setPosition] = useState<"bottom-right" | "bottom-left">("bottom-right");
  const [copied, setCopied] = useState(false);

  // API Keys tab state
  const [apiKey, setApiKey] = useState(MOCK_API_KEY);
  const [showKey, setShowKey] = useState(false);
  const [apiKeyCopied, setApiKeyCopied] = useState(false);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [showRegenDialog, setShowRegenDialog] = useState(false);

  const copyEmbed = async () => {
    await navigator.clipboard.writeText(EMBED_CODE);
    setCopied(true);
    toast.success("Copied to clipboard!");
    setTimeout(() => setCopied(false), 2000);
  };

  const copyApiKey = async () => {
    await navigator.clipboard.writeText(apiKey);
    setApiKeyCopied(true);
    toast.success("API key copied!");
    setTimeout(() => setApiKeyCopied(false), 2000);
  };

  const regenerateKey = () => {
    const newKey = "sk-ccn-" + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    setApiKey(newKey);
    setShowKey(true);
    setShowRegenDialog(false);
    toast.success("New API key generated!");
  };

  const maskedKey = apiKey.slice(0, 12) + "••••••••••••••••••••••••";

  return (
    <div className="space-y-6 max-w-3xl">
      <h2 className="text-xl font-semibold text-slate-800">Settings</h2>

      <Tabs defaultValue="widget">
        <TabsList className="mb-6">
          <TabsTrigger value="widget">Widget Config</TabsTrigger>
          <TabsTrigger value="embed">Embed Code</TabsTrigger>
          <TabsTrigger value="account">Account</TabsTrigger>
          <TabsTrigger value="api">API &amp; Webhooks</TabsTrigger>
        </TabsList>

        {/* Widget Config */}
        <TabsContent value="widget">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Widget Settings</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-1.5">
                  <Label>Display Name</Label>
                  <Input
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Support Bot"
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Welcome Message</Label>
                  <textarea
                    value={welcomeMessage}
                    onChange={(e) => setWelcomeMessage(e.target.value)}
                    rows={3}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
                    placeholder="Hello! How can I help you?"
                  />
                </div>

                <div className="space-y-1.5">
                  <Label>Primary Color</Label>
                  <div className="flex items-center gap-3">
                    <input
                      type="color"
                      value={primaryColor}
                      onChange={(e) => setPrimaryColor(e.target.value)}
                      className="h-9 w-16 rounded border border-input cursor-pointer"
                    />
                    <span className="text-sm text-slate-500 font-mono">{primaryColor}</span>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label>Position</Label>
                  <div className="flex gap-2">
                    {(["bottom-right", "bottom-left"] as const).map((pos) => (
                      <button
                        key={pos}
                        type="button"
                        onClick={() => setPosition(pos)}
                        className={`px-3 py-1.5 rounded-md text-sm border transition-colors ${
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

                <Button className="bg-indigo-600 hover:bg-indigo-700 text-white w-full mt-2"
                  onClick={() => toast.success("Settings saved!")}>
                  Save Changes
                </Button>
              </CardContent>
            </Card>

            {/* Live preview */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Live Preview</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="relative bg-slate-100 rounded-lg h-56 overflow-hidden">
                  <div className="p-3 space-y-1.5">
                    <div className="h-2 bg-slate-300 rounded w-24" />
                    <div className="h-2 bg-slate-200 rounded w-40" />
                    <div className="h-2 bg-slate-200 rounded w-32" />
                  </div>
                  <div className={`absolute bottom-4 ${position === "bottom-right" ? "right-4" : "left-4"}`}>
                    <div
                      className="w-11 h-11 rounded-full flex items-center justify-center shadow-lg cursor-pointer"
                      style={{ backgroundColor: primaryColor }}
                    >
                      <MessageSquare className="w-5 h-5 text-white" />
                    </div>
                  </div>
                </div>
                <p className="text-xs text-slate-400 text-center mt-2">
                  Widget position: {position}
                </p>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Embed Code */}
        <TabsContent value="embed">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Embed Code</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-slate-500">
                Add this snippet to your website just before the closing{" "}
                <code className="bg-slate-100 px-1 rounded text-xs">&lt;/body&gt;</code> tag.
              </p>
              <div className="relative">
                <pre className="bg-slate-900 text-slate-100 rounded-lg p-4 text-xs overflow-x-auto whitespace-pre-wrap">
                  {EMBED_CODE}
                </pre>
                <button
                  onClick={copyEmbed}
                  className="absolute top-3 right-3 p-1.5 rounded bg-slate-700 hover:bg-slate-600 text-slate-200 transition-colors"
                >
                  {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Account */}
        <TabsContent value="account">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Account Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label>Company Name</Label>
                <Input defaultValue="Acme Corporation" />
              </div>
              <div className="space-y-1.5">
                <Label>Email</Label>
                <Input defaultValue="admin@acmecorp.com" readOnly className="bg-slate-50" />
              </div>
              <div className="space-y-1.5">
                <Label>Plan</Label>
                <div>
                  <Badge className="bg-yellow-100 text-yellow-800 hover:bg-yellow-100 border-yellow-200">
                    TRIAL
                  </Badge>
                </div>
              </div>
              <Button className="bg-indigo-600 hover:bg-indigo-700 text-white"
                onClick={() => toast.success("Settings saved!")}>
                Save Changes
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* API & Webhooks */}
        <TabsContent value="api">
          <div className="space-y-4">
            {/* API Key */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Tenant API Key</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center gap-2">
                  <code className="flex-1 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5 text-sm font-mono text-slate-700 truncate">
                    {showKey ? apiKey : maskedKey}
                  </code>
                  <button
                    onClick={() => setShowKey((v) => !v)}
                    className="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-500 transition-colors shrink-0"
                    title={showKey ? "Hide key" : "Show key"}
                  >
                    {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                  <button
                    onClick={copyApiKey}
                    className="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-500 transition-colors shrink-0"
                    title="Copy key"
                  >
                    {apiKeyCopied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
                  </button>
                  <button
                    onClick={() => setShowRegenDialog(true)}
                    className="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-500 hover:text-red-500 transition-colors shrink-0"
                    title="Regenerate key"
                  >
                    <RefreshCw className="w-4 h-4" />
                  </button>
                </div>
                <div className="text-xs text-slate-500 flex gap-4">
                  <span>Created: Jan 15, 2026</span>
                  <span>Last used: 2 hours ago</span>
                </div>
                <p className="text-xs text-slate-400">
                  Keep this key secret. Never expose it in client-side code.
                </p>
              </CardContent>
            </Card>

            {/* Webhook URL */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Webhook Endpoint</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <p className="text-sm text-slate-500">
                  Receive a POST request when a conversation starts, ends, or is escalated.
                </p>
                <div className="flex gap-2">
                  <Input
                    value={webhookUrl}
                    onChange={(e) => setWebhookUrl(e.target.value)}
                    placeholder="https://your-server.com/webhook"
                    className="flex-1"
                  />
                  <Button
                    variant="outline"
                    onClick={() => toast.success("Webhook URL saved!")}
                  >
                    Save
                  </Button>
                </div>
              </CardContent>
            </Card>

            {/* Docs link */}
            <div className="flex items-center gap-2 text-sm text-indigo-600 hover:text-indigo-800 cursor-pointer">
              <ExternalLink className="w-4 h-4" />
              <span>View API documentation</span>
            </div>
          </div>
        </TabsContent>
      </Tabs>

      {/* Regenerate key confirmation */}
      <Dialog open={showRegenDialog} onOpenChange={setShowRegenDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Regenerate API key?</DialogTitle>
            <DialogDescription>
              This will immediately invalidate your current API key. Any integrations using the old key will stop working until updated.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowRegenDialog(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={regenerateKey}>
              Regenerate key
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
