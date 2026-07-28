import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { JobDescription, JobPostPresentation } from "./JobPostPresentation";

describe("job post presentation", () => {
  it("renders the server-sanitized rich representation with controlled typography", () => {
    const { container } = render(<JobDescription job={{ description: "Plain", descriptionHtml: "<h2>Role</h2><p>Build <strong>safely</strong> with <a href=\"https://example.com\" rel=\"nofollow noopener noreferrer\">docs</a>.</p>" }} />);
    expect(screen.getByRole("heading", { level: 2, name: "Role" })).toBeInTheDocument();
    expect(screen.getByText("safely").tagName).toBe("STRONG");
    expect(screen.getByRole("link", { name: "docs" })).toHaveAttribute("rel", "nofollow noopener noreferrer");
    expect(container.firstChild).toHaveClass("leading-7");
  });

  it("falls back to escaped whitespace-preserving plain text for legacy jobs", () => {
    const { container } = render(<JobDescription job={{ description: "First line\n<script>alert(1)</script>", descriptionHtml: null }} />);
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass("whitespace-pre-wrap");
  });

  it("shows preview status without an application action", () => {
    render(<JobPostPresentation job={{ title: "Engineer", companyName: "Acme", description: "Build", descriptionHtml: null, department: null, location: null, employmentType: null, workMode: null, closingAt: null }} previewLabel="Recruiter preview" previewStatus="PAUSED" />);
    expect(screen.getByRole("status")).toHaveTextContent("Recruiter preview · PAUSED");
    expect(screen.queryByRole("link", { name: /apply/i })).toBeNull();
  });
});
