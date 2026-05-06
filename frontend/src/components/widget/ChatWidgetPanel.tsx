"use client";

import { useState, useEffect, useRef } from "react";
import { useChatStore } from "@/components/providers/StoreProvider";
import { useShallow } from "zustand/react/shallow";
import type { Message } from "@/types";
import { MessageSquare, X, Send } from "lucide-react";

const MOCK_RESPONSES = [
  "Thank you for reaching out! Based on our documentation, I can help with that. Our return policy allows returns within 30 days of purchase in original condition.",
  "Great question! We support PDF, DOCX, and TXT files up to 50MB each. You can upload multiple documents at once using drag and drop.",
  "I'd be happy to help! You can find that information in your account settings under the Billing section. Let me know if you need further assistance.",
  "Absolutely! CacaNode supports over 50 languages and will automatically respond in the visitor's language regardless of the document language.",
];

function TypingIndicator() {
  return (
    <div className="flex gap-1 items-center px-3 py-2 bg-white rounded-xl shadow-sm w-fit">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce"
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
  botName = "Support Bot",
  alwaysOpen = false,
  fill = false,
}: ChatWidgetPanelProps) {
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

      const response = MOCK_RESPONSES[Math.floor(Math.random() * MOCK_RESPONSES.length)];
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
    botName,
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
        onClick={() => setIsOpen((o) => !o)}
        className="rounded-full flex items-center justify-center shadow-lg hover:opacity-90 transition-opacity"
        style={{ width: "52px", height: "52px", backgroundColor: primaryColor }}
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
            <p className="text-white/70 text-xs">Online</p>
          </div>
        </div>
        {onClose && (
          <button onClick={onClose} className="text-white/70 hover:text-white transition-colors">
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2 bg-slate-50 min-h-0">
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
            <TypingIndicator />
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
            placeholder="Type a message..."
            className="flex-1 text-sm px-3 py-1.5 rounded-lg border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            disabled={isStreaming || showTyping}
          />
          <button
            onClick={sendMessage}
            disabled={!inputValue.trim() || isStreaming || showTyping}
            className="p-1.5 text-white rounded-lg hover:opacity-90 disabled:opacity-40 transition-opacity"
            style={{ backgroundColor: primaryColor }}
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
