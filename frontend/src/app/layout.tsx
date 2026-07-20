"use client";

import "./globals.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useState } from "react";
import { PageTitle } from "@/components/app/PageTitle";
import { StoreProvider } from "@/components/providers/StoreProvider";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <html lang="en">
      <body className="font-sans" suppressHydrationWarning>
        <PageTitle />
        <QueryClientProvider client={queryClient}>
          <StoreProvider>
            {children}
          </StoreProvider>
          <Toaster position="top-right" />
        </QueryClientProvider>
      </body>
    </html>
  );
}
