"use client";

import { useEffect, useState } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Link from "@tiptap/extension-link";
import {
  Bold, Heading2, Heading3, Italic, Link2, List, ListOrdered, Pilcrow,
  Quote, Redo2, Undo2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

type Props = {
  id: string;
  value: string | null;
  legacyPlainText: string;
  disabled?: boolean;
  locale: string;
  onChange: (html: string, plainText: string) => void;
};

export function plainTextToJobHtml(value: string) {
  const escape = (text: string) => text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  return value.split(/\n{2,}/).map((block) => `<p>${escape(block).replaceAll("\n", "<br>")}</p>`).join("");
}

function safeHref(value: string) {
  const trimmed = value.trim();
  const candidate = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)
    ? `mailto:${trimmed}`
    : /^[\w.-]+\.[a-z]{2,}(?:\/|$)/i.test(trimmed) ? `https://${trimmed}` : trimmed;
  try {
    const url = new URL(candidate);
    return ["http:", "https:", "mailto:"].includes(url.protocol) ? candidate : null;
  } catch {
    return null;
  }
}

export function RichJobDescriptionEditor({ id, value, legacyPlainText, disabled, locale, onChange }: Props) {
  const vi = locale.startsWith("vi");
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [linkValue, setLinkValue] = useState("");
  const [linkError, setLinkError] = useState("");
  const editor = useEditor({
    immediatelyRender: false,
    editable: !disabled,
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] }, code: false, codeBlock: false,
        horizontalRule: false, strike: false, link: false,
      }),
      Link.configure({
        openOnClick: false,
        autolink: true,
        defaultProtocol: "https",
        protocols: ["http", "https", "mailto"],
        HTMLAttributes: { rel: "nofollow noopener noreferrer" },
        isAllowedUri: (url) => safeHref(url) !== null,
      }),
    ],
    content: value ?? plainTextToJobHtml(legacyPlainText),
    onUpdate: ({ editor: current }) => onChange(current.getHTML(), current.getText({ blockSeparator: "\n" }).trim()),
    editorProps: {
      attributes: {
        id,
        role: "textbox",
        "aria-multiline": "true",
        "aria-label": vi ? "Nội dung mô tả công việc" : "Job description content",
        class: "min-h-56 px-4 py-3 text-sm leading-7 outline-none [&_a]:font-medium [&_a]:text-indigo-700 [&_a]:underline [&_blockquote]:my-4 [&_blockquote]:border-l-4 [&_blockquote]:border-indigo-200 [&_blockquote]:pl-4 [&_h2]:mb-2 [&_h2]:mt-5 [&_h2]:text-xl [&_h2]:font-bold [&_h3]:mb-2 [&_h3]:mt-4 [&_h3]:text-lg [&_h3]:font-semibold [&_ol]:my-3 [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:my-2 [&_ul]:my-3 [&_ul]:list-disc [&_ul]:pl-6",
      },
    },
  });

  useEffect(() => { editor?.setEditable(!disabled); }, [disabled, editor]);
  useEffect(() => {
    if (!editor) return;
    const next = value ?? plainTextToJobHtml(legacyPlainText);
    if (editor.getHTML() !== next) editor.commands.setContent(next, { emitUpdate: false });
  }, [editor, legacyPlainText, value]);

  if (!editor) return <div className="min-h-56 animate-pulse rounded-lg border bg-muted/30" aria-busy="true" />;

  const actions = [
    { label: vi ? "Đoạn văn" : "Paragraph", icon: Pilcrow, active: editor.isActive("paragraph"), run: () => editor.chain().focus().setParagraph().run() },
    { label: vi ? "Tiêu đề cấp 2" : "Heading 2", icon: Heading2, active: editor.isActive("heading", { level: 2 }), run: () => editor.chain().focus().toggleHeading({ level: 2 }).run() },
    { label: vi ? "Tiêu đề cấp 3" : "Heading 3", icon: Heading3, active: editor.isActive("heading", { level: 3 }), run: () => editor.chain().focus().toggleHeading({ level: 3 }).run() },
    { label: vi ? "In đậm" : "Bold", icon: Bold, active: editor.isActive("bold"), run: () => editor.chain().focus().toggleBold().run() },
    { label: vi ? "In nghiêng" : "Italic", icon: Italic, active: editor.isActive("italic"), run: () => editor.chain().focus().toggleItalic().run() },
    { label: vi ? "Danh sách chấm" : "Bullet list", icon: List, active: editor.isActive("bulletList"), run: () => editor.chain().focus().toggleBulletList().run() },
    { label: vi ? "Danh sách số" : "Numbered list", icon: ListOrdered, active: editor.isActive("orderedList"), run: () => editor.chain().focus().toggleOrderedList().run() },
    { label: vi ? "Trích dẫn" : "Block quote", icon: Quote, active: editor.isActive("blockquote"), run: () => editor.chain().focus().toggleBlockquote().run() },
  ];

  function editLink() {
    if (!editor) return;
    const previous = editor.getAttributes("link").href as string | undefined;
    setLinkValue(previous ?? "https://");
    setLinkError("");
    setLinkDialogOpen(true);
  }

  function applyLink() {
    if (!editor) return;
    if (!linkValue.trim()) {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      setLinkDialogOpen(false);
      return;
    }
    const href = safeHref(linkValue.trim());
    if (!href) {
      setLinkError(vi ? "Hãy dùng liên kết HTTP, HTTPS hoặc email hợp lệ." : "Use a valid HTTP, HTTPS, or email link.");
      return;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href }).run();
    setLinkDialogOpen(false);
  }

  return <div className="overflow-hidden rounded-lg border border-input bg-background focus-within:ring-2 focus-within:ring-ring/40">
    <div role="toolbar" aria-label={vi ? "Định dạng mô tả công việc" : "Job description formatting"} className="flex flex-wrap gap-1 border-b bg-muted/30 p-2">
      {actions.map(({ label, icon: Icon, active, run }) => <Button key={label} type="button" size="icon-sm" variant={active ? "secondary" : "ghost"} aria-label={label} title={label} aria-pressed={active} disabled={disabled} onClick={run}><Icon /></Button>)}
      <Button type="button" size="icon-sm" variant={editor.isActive("link") ? "secondary" : "ghost"} aria-label={vi ? "Thêm hoặc sửa liên kết" : "Add or edit link"} title={vi ? "Liên kết" : "Link"} aria-pressed={editor.isActive("link")} disabled={disabled} onClick={editLink}><Link2 /></Button>
      <span className="mx-1 w-px bg-border" aria-hidden="true" />
      <Button type="button" size="icon-sm" variant="ghost" aria-label={vi ? "Hoàn tác" : "Undo"} title={vi ? "Hoàn tác" : "Undo"} disabled={disabled || !editor.can().chain().focus().undo().run()} onClick={() => editor.chain().focus().undo().run()}><Undo2 /></Button>
      <Button type="button" size="icon-sm" variant="ghost" aria-label={vi ? "Làm lại" : "Redo"} title={vi ? "Làm lại" : "Redo"} disabled={disabled || !editor.can().chain().focus().redo().run()} onClick={() => editor.chain().focus().redo().run()}><Redo2 /></Button>
    </div>
    <EditorContent editor={editor} />
    <Dialog open={linkDialogOpen} onOpenChange={setLinkDialogOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{vi ? "Thêm liên kết" : "Add link"}</DialogTitle>
          <DialogDescription>{vi ? "Chỉ hỗ trợ liên kết HTTP, HTTPS và địa chỉ email an toàn." : "Only safe HTTP, HTTPS, and email links are supported."}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor={`${id}-link`}>{vi ? "Địa chỉ liên kết" : "Link address"}</Label>
          <Input id={`${id}-link`} type="url" value={linkValue} aria-invalid={Boolean(linkError)} aria-describedby={linkError ? `${id}-link-error` : undefined} onChange={(event) => { setLinkValue(event.target.value); setLinkError(""); }} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); applyLink(); } }} />
          {linkError && <p id={`${id}-link-error`} role="alert" className="text-sm text-red-600">{linkError}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setLinkDialogOpen(false)}>{vi ? "Hủy" : "Cancel"}</Button>
          <Button onClick={applyLink}>{vi ? "Áp dụng" : "Apply"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>;
}
