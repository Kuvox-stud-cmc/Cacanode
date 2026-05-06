"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { Bot, Menu, X } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function PublicNavbar() {
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
          <img src="/logo.png" alt="CacaNode Logo" className="w-7 h-7" />
          <span className="font-bold text-lg text-slate-900">CacaNode</span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden md:flex items-center gap-8 text-sm">
          <Link href="/#features" className="text-slate-600 hover:text-slate-900 transition-colors">
            Features
          </Link>
          <Link href="/#how-it-works" className="text-slate-600 hover:text-slate-900 transition-colors">
            How It Works
          </Link>
          <Link href="/pricing" className="text-slate-600 hover:text-slate-900 transition-colors">
            Pricing
          </Link>
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
          <a href="#features" className="block text-sm text-slate-700 py-2" onClick={() => setMobileOpen(false)}>
            Features
          </a>
          <a href="#how-it-works" className="block text-sm text-slate-700 py-2" onClick={() => setMobileOpen(false)}>
            How It Works
          </a>
          <Link href="/pricing" className="block text-sm text-slate-700 py-2" onClick={() => setMobileOpen(false)}>
            Pricing
          </Link>
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
