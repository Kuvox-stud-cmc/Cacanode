import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  BEFORE_LOCALE_CHANGE_EVENT,
  localeDraftStorageKey,
  useLocaleChangeDraft,
} from "./useLocaleChangeDraft";

describe("useLocaleChangeDraft", () => {
  beforeEach(() => window.sessionStorage.clear());

  it("hands the latest form value to the next locale route mount", () => {
    const restore = vi.fn();
    const first = renderHook(
      ({ value }) => useLocaleChangeDraft("recruitment:test", value, restore),
      { initialProps: { value: { title: "Initial" } } },
    );

    first.rerender({ value: { title: "Unsaved job" } });
    act(() => window.dispatchEvent(new Event(BEFORE_LOCALE_CHANGE_EVENT)));
    first.unmount();

    const nextRestore = vi.fn();
    renderHook(() => useLocaleChangeDraft("recruitment:test", { title: "" }, nextRestore));

    expect(nextRestore).toHaveBeenCalledWith({ title: "Unsaved job" });
  });

  it("clears a saved or cancelled draft", () => {
    const { result } = renderHook(() => useLocaleChangeDraft("recruitment:test", { title: "Draft" }, vi.fn()));
    act(() => window.dispatchEvent(new Event(BEFORE_LOCALE_CHANGE_EVENT)));
    expect(window.sessionStorage.getItem(localeDraftStorageKey("recruitment:test"))).not.toBeNull();

    act(() => result.current());

    expect(window.sessionStorage.getItem(localeDraftStorageKey("recruitment:test"))).toBeNull();
  });

  it("preserves rich HTML fields across a locale remount", () => {
    const restore = vi.fn();
    const value = { description: "Role requirements", descriptionHtml: "<h2>Requirements</h2><ul><li>Java</li></ul>" };
    const first = renderHook(() => useLocaleChangeDraft("recruitment:rich-job", value, vi.fn()));
    act(() => window.dispatchEvent(new Event(BEFORE_LOCALE_CHANGE_EVENT)));
    first.unmount();
    renderHook(() => useLocaleChangeDraft("recruitment:rich-job", { description: "", descriptionHtml: null as string | null }, restore));
    expect(restore).toHaveBeenCalledWith(value);
  });

  it("ignores and removes malformed stored drafts", () => {
    const key = localeDraftStorageKey("recruitment:test");
    window.sessionStorage.setItem(key, "not-json");
    const restore = vi.fn();

    renderHook(() => useLocaleChangeDraft("recruitment:test", { title: "" }, restore));

    expect(restore).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem(key)).toBeNull();
  });
});
