import ChatWidgetPanel from "@/components/widget/ChatWidgetPanel";

export default function WidgetPreviewPage() {
  return (
    <div className="min-h-screen bg-slate-200 relative overflow-hidden">
      {/* Mock website content */}
      <header className="bg-white shadow-sm px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 bg-indigo-600 rounded" />
          <span className="font-semibold text-slate-800">Acme Corp</span>
        </div>
        <nav className="hidden md:flex gap-6 text-sm text-slate-600">
          <span className="cursor-pointer hover:text-slate-900">Home</span>
          <span className="cursor-pointer hover:text-slate-900">Products</span>
          <span className="cursor-pointer hover:text-slate-900">Support</span>
          <span className="cursor-pointer hover:text-slate-900">Contact</span>
        </nav>
      </header>

      <main className="max-w-4xl mx-auto py-16 px-6 space-y-6">
        <div className="bg-white rounded-xl p-8 shadow-sm">
          <div className="h-4 bg-slate-200 rounded w-1/3 mb-4" />
          <div className="space-y-2">
            <div className="h-3 bg-slate-100 rounded" />
            <div className="h-3 bg-slate-100 rounded w-5/6" />
            <div className="h-3 bg-slate-100 rounded w-4/6" />
          </div>
        </div>
        <div className="grid grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-white rounded-xl p-5 shadow-sm">
              <div className="h-3 bg-slate-200 rounded w-2/3 mb-3" />
              <div className="space-y-1.5">
                <div className="h-2 bg-slate-100 rounded" />
                <div className="h-2 bg-slate-100 rounded w-4/5" />
              </div>
            </div>
          ))}
        </div>
      </main>

      {/* Chat widget — bottom right */}
      <div className="fixed bottom-5 right-5 z-50">
        <ChatWidgetPanel primaryColor="#4f46e5" botName="Support Bot" />
      </div>
    </div>
  );
}
