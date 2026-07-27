import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { useRecruitmentConfirmation } from "./useRecruitmentConfirmation";

vi.mock("next-intl", () => ({ useTranslations: () => (key: string) => key === "cancel" ? "Cancel" : key }));

function Harness() {
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();
  const [result, setResult] = useState("pending");
  return <>
    <button onClick={async () => {
      const accepted = await confirm({ title: "Pause job?", description: "Applicants will no longer be able to apply.", confirmLabel: "Pause", destructive: true });
      setResult(String(accepted));
    }}>Open</button>
    <span>{result}</span>
    {confirmationDialog}
  </>;
}

describe("recruitment confirmation dialog", () => {
  it("replaces browser confirmation with an accessible in-app dialog", async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("dialog", { name: "Pause job?" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Pause" }));
    expect(await screen.findByText("true")).toBeInTheDocument();
  });
});
