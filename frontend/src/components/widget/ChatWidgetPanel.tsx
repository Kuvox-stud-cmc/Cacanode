"use client";

import { useState, useEffect, useRef } from "react";
import { useLocale } from "next-intl";
import { useChatStore } from "@/components/providers/StoreProvider";
import { useShallow } from "zustand/react/shallow";
import type { Message } from "@/types";
import { MessageSquare, X, Send } from "lucide-react";

type WidgetLocale = "en" | "vi";

const WIDGET_COPY = {
  en: {
    supportBot: "Support Bot", online: "Online", placeholder: "Type a message…", thinking: "Thinking…",
    open: "Open support chat", close: "Close support chat", send: "Send message", language: "Language",
    responses: [
      "Thank you for reaching out! Based on our documentation, our return policy allows returns within 30 days of purchase in original condition.",
      "Great question! We support PDF, DOCX, and TXT files up to 50MB each. You can upload multiple documents at once.",
      "You can find that information in your account settings under Billing. Let me know if you need more help.",
      "CacaNode supports over 50 languages and can respond in the visitor's language regardless of the document language.",
    ],
  },
  vi: {
    supportBot: "Bot hỗ trợ", online: "Đang trực tuyến", placeholder: "Nhập tin nhắn…", thinking: "Đang suy nghĩ…",
    open: "Mở trò chuyện hỗ trợ", close: "Đóng trò chuyện hỗ trợ", send: "Gửi tin nhắn", language: "Ngôn ngữ",
    responses: [
      "Cảm ơn bạn đã liên hệ! Theo tài liệu của chúng tôi, sản phẩm còn nguyên trạng có thể được đổi trả trong vòng 30 ngày kể từ ngày mua.",
      "Câu hỏi hay! Chúng tôi hỗ trợ tệp PDF, DOCX và TXT tối đa 50MB mỗi tệp. Bạn có thể tải nhiều tài liệu cùng lúc.",
      "Bạn có thể tìm thông tin đó trong phần Thanh toán của cài đặt tài khoản. Hãy cho tôi biết nếu bạn cần thêm trợ giúp.",
      "CacaNode hỗ trợ hơn 50 ngôn ngữ và có thể phản hồi bằng ngôn ngữ của khách truy cập bất kể ngôn ngữ tài liệu.",
    ],
  },
} as const;

function TypingIndicator({ label }: { label: string }) {
  return (
    <div className="flex gap-1 items-center px-3 py-2 bg-white rounded-xl shadow-sm w-fit" role="status" aria-label={label}>
      <span className="sr-only">{label}</span>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce motion-reduce:animate-none"
          style={{ animationDelay: `${i * 150}ms` }}
        />
      ))}
    </div>
  );
}

interface ChatWidgetPanelProps {
  primaryColor?: string;
  botName?: string;
  /** Render the chat window directly without the floating bubble toggle */
  alwaysOpen?: boolean;
  /** Stretch to fill the parent container instead of using fixed w-80/h-[420px] */
  fill?: boolean;
}

export default function ChatWidgetPanel({
  primaryColor = "#4f46e5",
  botName,
  alwaysOpen = false,
  fill = false,
}: ChatWidgetPanelProps) {
  const dashboardLocale = useLocale();
  const [widgetLocale, setWidgetLocale] = useState<WidgetLocale>(dashboardLocale === "vi" ? "vi" : "en");
  const copy = WIDGET_COPY[widgetLocale];
  const [isOpen, setIsOpen] = useState(false);
  const [inputValue, setInputValue] = useState("");
  const [showTyping, setShowTyping] = useState(false);
  const {
    messages,
    addMessage,
    isStreaming,
    streamingContent,
    startStreaming,
    appendChunk,
    finishStreaming,
  } = useChatStore(
    useShallow((s) => ({
      messages: s.messages,
      addMessage: s.addMessage,
      isStreaming: s.isStreaming,
      streamingContent: s.streamingContent,
      startStreaming: s.startStreaming,
      appendChunk: s.appendChunk,
      finishStreaming: s.finishStreaming,
    }))
  );
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingContent, showTyping]);

  const sendMessage = () => {
    const text = inputValue.trim();
    if (!text || isStreaming || showTyping) return;

    addMessage({
      id: `msg-${Date.now()}-${Math.random()}`,
      role: "user",
      content: text,
      timestamp: new Date().toISOString(),
    });
    setInputValue("");

    setShowTyping(true);
    setTimeout(() => {
      setShowTyping(false);
      startStreaming();

      const response = copy.responses[Math.floor(Math.random() * copy.responses.length)];
      const words = response.split(" ");
      let index = 0;
      const interval = setInterval(() => {
        if (index < words.length) {
          appendChunk((index === 0 ? "" : " ") + words[index]);
          index++;
        } else {
          clearInterval(interval);
          finishStreaming();
        }
      }, 60);
    }, 1000);
  };

  const sharedProps = {
    botName: botName ?? copy.supportBot,
    primaryColor,
    messages,
    isStreaming,
    streamingContent,
    showTyping,
    inputValue,
    setInputValue,
    sendMessage,
    messagesEndRef,
    fill,
    widgetLocale,
    setWidgetLocale,
    copy,
  };

  if (alwaysOpen || fill) {
    return <ChatWindow {...sharedProps} />;
  }

  return (
    <div className="flex flex-col items-end gap-3">
      {isOpen && (
        <ChatWindow
          {...sharedProps}
          onClose={() => setIsOpen(false)}
        />
      )}
      <button
        type="button"
        onClick={() => setIsOpen((o) => !o)}
        className="rounded-full flex items-center justify-center shadow-lg hover:opacity-90 transition-opacity"
        style={{ width: "52px", height: "52px", backgroundColor: primaryColor }}
        aria-label={isOpen ? copy.close : copy.open}
      >
        {isOpen ? (
          <X className="w-5 h-5 text-white" />
        ) : (
          <MessageSquare className="w-5 h-5 text-white" />
        )}
      </button>
    </div>
  );
}

interface ChatWindowProps {
  botName: string;
  primaryColor: string;
  messages: Message[];
  isStreaming: boolean;
  streamingContent: string;
  showTyping: boolean;
  inputValue: string;
  setInputValue: (v: string) => void;
  sendMessage: () => void;
  onClose?: () => void;
  messagesEndRef: React.RefObject<HTMLDivElement | null>;
  fill?: boolean;
  widgetLocale: WidgetLocale;
  setWidgetLocale: (locale: WidgetLocale) => void;
  copy: (typeof WIDGET_COPY)[WidgetLocale];
}

function ChatWindow({
  botName,
  primaryColor,
  messages,
  isStreaming,
  streamingContent,
  showTyping,
  inputValue,
  setInputValue,
  sendMessage,
  onClose,
  messagesEndRef,
  fill,
  widgetLocale,
  setWidgetLocale,
  copy,
}: ChatWindowProps) {
  const containerClass = fill
    ? "flex flex-col w-full h-full bg-white"
    : "w-80 bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden";

  const containerStyle = fill ? {} : { height: "420px" };

  return (
    <div className={containerClass} style={containerStyle}>
      {/* Header */}
      <div
        className="flex items-center justify-between px-4 py-3 shrink-0"
        style={{ backgroundColor: primaryColor }}
      >
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 bg-white/20 rounded-full flex items-center justify-center">
            <MessageSquare className="w-4 h-4 text-white" />
          </div>
          <div>
            <p className="text-white font-semibold text-sm">{botName}</p>
            <p className="text-white/70 text-xs">{copy.online}</p>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="flex items-center rounded-full border border-white/30 bg-white/10 p-0.5 text-[10px] font-bold text-white/80" role="group" aria-label={copy.language}>
            {(["en", "vi"] as const).map((locale) => <button key={locale} type="button" aria-pressed={widgetLocale === locale} onClick={() => setWidgetLocale(locale)} className={`rounded-full px-1.5 py-1 ${widgetLocale === locale ? "bg-white text-slate-800" : "hover:bg-white/10"}`}>{locale.toUpperCase()}</button>)}
          </div>
        {onClose && (
          <button type="button" onClick={onClose} className="text-white/70 hover:text-white transition-colors" aria-label={copy.close}>
            <X className="w-4 h-4" />
          </button>
        )}
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2 bg-slate-50 min-h-0" aria-live="polite" aria-busy={showTyping || isStreaming}>
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[80%] px-3 py-2 rounded-xl text-sm ${
                msg.role === "user"
                  ? "text-white rounded-br-none"
                  : "bg-white text-slate-700 shadow-sm rounded-bl-none"
              }`}
              style={msg.role === "user" ? { backgroundColor: primaryColor } : {}}
            >
              {msg.content}
            </div>
          </div>
        ))}

        {showTyping && (
          <div className="flex justify-start">
            <TypingIndicator label={copy.thinking} />
          </div>
        )}

        {isStreaming && streamingContent && (
          <div className="flex justify-start">
            <div className="max-w-[80%] px-3 py-2 rounded-xl text-sm bg-white text-slate-700 shadow-sm rounded-bl-none">
              {streamingContent}
              <span className="inline-block w-1 h-3 bg-indigo-500 ml-0.5 animate-pulse" />
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="p-3 bg-white border-t border-slate-100 shrink-0">
        <div className="flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && sendMessage()}
            placeholder={copy.placeholder}
            aria-label={copy.placeholder}
            className="flex-1 text-sm px-3 py-1.5 rounded-lg border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            disabled={isStreaming || showTyping}
          />
          <button
            type="button"
            onClick={sendMessage}
            disabled={!inputValue.trim() || isStreaming || showTyping}
            className="p-1.5 text-white rounded-lg hover:opacity-90 disabled:opacity-40 transition-opacity"
            style={{ backgroundColor: primaryColor }}
            aria-label={copy.send}
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
