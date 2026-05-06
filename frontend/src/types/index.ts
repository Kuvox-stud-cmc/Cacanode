export interface Document {
  id: string;
  fileName: string;
  fileType: string;
  status: "pending" | "processing" | "completed" | "failed";
  fileSizeBytes: number;
  jobId: string;
  uploadedAt: string;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: "admin" | "user";
  status: "active" | "inactive";
  joinedAt: string;
}

/** Matches Spring Boot `AuthResponse.user` (JWT login/register/refresh). */
export interface AuthUser {
  userId: string;
  tenantId: string;
  email: string;
  fullName: string;
  role: string;
  plan: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  isStreaming?: boolean;
}

export interface StatsCard {
  label: string;
  value: string | number;
  icon: string;
  trend: "up" | "down" | "neutral";
  trendValue: string;
}

export interface WidgetConfig {
  displayName: string;
  welcomeMessage: string;
  primaryColor: string;
  position: "bottom-right" | "bottom-left";
  isActive: boolean;
}

export interface TenantInfo {
  id: string;
  name: string;
  email: string;
  plan: "trial" | "starter" | "pro" | "enterprise";
  status: "active" | "suspended";
}

export interface Conversation {
  id: string;
  visitorId: string;
  messageCount: number;
  startedAt: string;
  durationSeconds: number;
  status: "open" | "resolved";
  messages: Message[];
}

export interface DailyVolume {
  date: string;
  count: number;
}

export interface PopularQuestion {
  question: string;
  count: number;
}

export interface Testimonial {
  quote: string;
  name: string;
  title: string;
  company: string;
  initials: string;
}

export interface PricingPlan {
  name: string;
  price: string;
  description: string;
  features: string[];
  cta: string;
  highlighted: boolean;
}
