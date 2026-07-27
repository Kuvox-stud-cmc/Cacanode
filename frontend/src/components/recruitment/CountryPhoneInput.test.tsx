import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CountryPhoneInput } from "./CountryPhoneInput";

describe("CountryPhoneInput", () => {
  it("shows a localized country selector and keeps the calling code outside the input", () => {
    const onCountryChange = vi.fn();
    const onNationalNumberChange = vi.fn();
    render(<CountryPhoneInput country="VN" nationalNumber="0901234567" locale="vi" countryLabel="Quốc gia hoặc khu vực" numberLabel="Số điện thoại" help="Nhập số trong nước" invalidMessage="Không hợp lệ" onCountryChange={onCountryChange} onNationalNumberChange={onNationalNumberChange} />);

    expect(screen.getByRole("combobox", { name: "Quốc gia hoặc khu vực" })).toHaveValue("VN");
    expect(screen.getByRole("option", { name: /Việt Nam \(\+84\)/ })).toBeInTheDocument();
    expect(screen.getByText("+84")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Số điện thoại" })).toHaveValue("0901234567");

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "US" } });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "(415) 555-2671" } });
    expect(onCountryChange).toHaveBeenCalledWith("US");
    expect(onNationalNumberChange).toHaveBeenCalledWith("(415) 555-2671");
  });
});
