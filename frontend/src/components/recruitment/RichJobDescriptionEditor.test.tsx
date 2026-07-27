import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { plainTextToJobHtml, RichJobDescriptionEditor } from "./RichJobDescriptionEditor";

vi.mock("next-intl", () => ({ useTranslations: () => (key: string) => key }));

describe("rich job description editor", () => {
  it("initializes legacy plain text as escaped paragraphs and line breaks", () => {
    expect(plainTextToJobHtml("First <role>\nline\n\nSecond & final"))
      .toBe("<p>First &lt;role&gt;<br>line</p><p>Second &amp; final</p>");
  });

  it("exposes the focused accessible bilingual toolbar and legacy content", async () => {
    render(<RichJobDescriptionEditor id="description" value={null} legacyPlainText="Legacy description" locale="vi" onChange={vi.fn()} />);
    expect(await screen.findByRole("toolbar", { name: "Định dạng mô tả công việc" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "In đậm" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Thêm hoặc sửa liên kết" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Nội dung mô tả công việc" })).toHaveTextContent("Legacy description");
  });

  it("uses an in-app link dialog instead of browser prompts", async () => {
    render(<RichJobDescriptionEditor id="description" value={null} legacyPlainText="Role" locale="en" onChange={vi.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Add or edit link" }));
    expect(screen.getByRole("dialog", { name: "Add link" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Link address" })).toHaveValue("https://");
  });
});
