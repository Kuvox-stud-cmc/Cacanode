"use client";

import { useState, useEffect } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, X } from "lucide-react";
import { Button } from "@/components/ui/button";

const navigation = [
  { href: "/", label: "Chat Playground" },
  { href: "/pricing", label: "Pricing" },
];

export default function PublicNavbar() {
  const pathname = usePathname();
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={`fixed top-0 inset-x-0 z-50 transition-all duration-200 ${
        scrolled ? "bg-white/90 backdrop-blur-md shadow-sm" : "bg-transparent"
      }`}
    >
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2">
          <Image src="/logo.png" alt="CacaNode Logo" width={28} height={28} />
          <span className="font-bold text-lg text-slate-900">CacaNode</span>
        </Link>

        {/* Desktop nav */}
        <nav aria-label="Primary navigation" className="hidden items-center gap-2 text-sm md:flex">
          {navigation.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-md px-3 py-2 font-medium transition-colors ${
                  active
                    ? "bg-indigo-50 text-indigo-700"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* Desktop CTAs */}
        <div className="hidden md:flex items-center gap-3">
          <Link href="/login">
            <Button variant="ghost" size="sm">
              Log in
            </Button>
          </Link>
          <Link href="/register">
            <Button size="sm" className="bg-indigo-600 hover:bg-indigo-700 text-white">
              Get Started
            </Button>
          </Link>
        </div>

        {/* Mobile hamburger */}
        <button
          className="md:hidden p-1.5 rounded-md text-slate-600 hover:bg-slate-100"
          onClick={() => setMobileOpen((o) => !o)}
        >
          {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="md:hidden bg-white border-t border-slate-100 px-4 py-4 space-y-3">
          {navigation.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={pathname === item.href ? "page" : undefined}
              className={`block rounded-md px-3 py-2 text-sm font-medium ${
                pathname === item.href ? "bg-indigo-50 text-indigo-700" : "text-slate-700"
              }`}
              onClick={() => setMobileOpen(false)}
            >
              {item.label}
            </Link>
          ))}
          <div className="flex gap-2 pt-2">
            <Link href="/login" className="flex-1">
              <Button variant="outline" size="sm" className="w-full">
                Log in
              </Button>
            </Link>
            <Link href="/register" className="flex-1">
              <Button size="sm" className="w-full bg-indigo-600 hover:bg-indigo-700 text-white">
                Get Started
              </Button>
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
