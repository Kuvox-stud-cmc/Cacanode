import type {
  User,
  Message,
  Conversation,
  Testimonial,
} from "@/types";

export const mockUsers: User[] = [
  {
    id: "user-1",
    email: "alice@example.com",
    fullName: "Alice Johnson",
    role: "TENANT_ADMIN",
    status: "ACTIVE",
    joinedAt: "2026-01-15T08:00:00Z",
  },
  {
    id: "user-2",
    email: "bob@example.com",
    fullName: "Bob Smith",
    role: "USER",
    status: "ACTIVE",
    joinedAt: "2026-02-20T09:30:00Z",
  },
  {
    id: "user-3",
    email: "carol@example.com",
    fullName: "Carol White",
    role: "USER",
    status: "ACTIVE",
    joinedAt: "2026-03-10T14:00:00Z",
  },
  {
    id: "user-4",
    email: "david@example.com",
    fullName: "David Lee",
    role: "TENANT_ADMIN",
    status: "INACTIVE",
    joinedAt: "2026-03-25T11:15:00Z",
  },
];

export const mockMessages: Message[] = [
  {
    id: "msg-1",
    role: "assistant",
    content: "Hello! How can I help you today?",
    timestamp: "2026-05-04T10:00:00Z",
  },
  {
    id: "msg-2",
    role: "user",
    content: "What is your return policy?",
    timestamp: "2026-05-04T10:01:00Z",
  },
  {
    id: "msg-3",
    role: "assistant",
    content:
      "Our return policy allows returns within 30 days of purchase. Items must be in original condition with tags attached. Refunds are processed within 5-7 business days once we receive the item.",
    timestamp: "2026-05-04T10:01:15Z",
  },
];

export const mockConversations: Conversation[] = [
  {
    id: "conv-1",
    visitorId: "visitor_8f3a2b",
    messageCount: 6,
    startedAt: "2026-05-04T08:12:00Z",
    durationSeconds: 192,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-04T08:12:00Z" },
      { id: "m2", role: "user", content: "How do I cancel my subscription?", timestamp: "2026-05-04T08:12:30Z" },
      { id: "m3", role: "assistant", content: "You can cancel your subscription by going to Settings → Billing → Cancel Plan. The cancellation takes effect at the end of your current billing period.", timestamp: "2026-05-04T08:12:45Z" },
      { id: "m4", role: "user", content: "Will I lose my data immediately?", timestamp: "2026-05-04T08:13:10Z" },
      { id: "m5", role: "assistant", content: "No, your data is retained for 30 days after cancellation. You can export everything during that period.", timestamp: "2026-05-04T08:13:25Z" },
      { id: "m6", role: "user", content: "Great, thanks!", timestamp: "2026-05-04T08:13:50Z" },
    ],
  },
  {
    id: "conv-2",
    visitorId: "visitor_c91d4e",
    messageCount: 4,
    startedAt: "2026-05-04T09:30:00Z",
    durationSeconds: 145,
    status: "open",
    messages: [
      { id: "m1", role: "assistant", content: "Hi there! What can I help you with?", timestamp: "2026-05-04T09:30:00Z" },
      { id: "m2", role: "user", content: "Do you support PDF uploads?", timestamp: "2026-05-04T09:30:20Z" },
      { id: "m3", role: "assistant", content: "Yes! We support PDF, DOCX, and TXT files up to 50MB each.", timestamp: "2026-05-04T09:30:35Z" },
      { id: "m4", role: "user", content: "What about images inside the PDFs?", timestamp: "2026-05-04T09:31:10Z" },
    ],
  },
  {
    id: "conv-3",
    visitorId: "visitor_77ab1f",
    messageCount: 3,
    startedAt: "2026-05-04T10:05:00Z",
    durationSeconds: 78,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-04T10:05:00Z" },
      { id: "m2", role: "user", content: "What plans do you offer?", timestamp: "2026-05-04T10:05:18Z" },
      { id: "m3", role: "assistant", content: "We offer Starter (free), Pro ($49/mo), and Enterprise (custom pricing). You can compare all features on our pricing page.", timestamp: "2026-05-04T10:05:35Z" },
    ],
  },
  {
    id: "conv-4",
    visitorId: "visitor_2e5c9d",
    messageCount: 8,
    startedAt: "2026-05-03T14:22:00Z",
    durationSeconds: 387,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-03T14:22:00Z" },
      { id: "m2", role: "user", content: "I'm having trouble setting up the widget on my site.", timestamp: "2026-05-03T14:22:25Z" },
      { id: "m3", role: "assistant", content: "I'd be happy to help! What issue are you seeing?", timestamp: "2026-05-03T14:22:40Z" },
      { id: "m4", role: "user", content: "The widget isn't showing up at all.", timestamp: "2026-05-03T14:23:00Z" },
      { id: "m5", role: "assistant", content: "Please make sure the script tag is placed just before the closing </body> tag. Also check that your tenant key is correct.", timestamp: "2026-05-03T14:23:20Z" },
      { id: "m6", role: "user", content: "Oh I put it in the <head> tag!", timestamp: "2026-05-03T14:23:50Z" },
      { id: "m7", role: "assistant", content: "That's the issue! Move it to just before </body> and it should work.", timestamp: "2026-05-03T14:24:05Z" },
      { id: "m8", role: "user", content: "It works now, thank you!", timestamp: "2026-05-03T14:25:10Z" },
    ],
  },
  {
    id: "conv-5",
    visitorId: "visitor_b3f1e8",
    messageCount: 2,
    startedAt: "2026-05-03T16:45:00Z",
    durationSeconds: 42,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-03T16:45:00Z" },
      { id: "m2", role: "user", content: "Where can I find my invoice?", timestamp: "2026-05-03T16:45:18Z" },
    ],
  },
  {
    id: "conv-6",
    visitorId: "visitor_a4d2c7",
    messageCount: 5,
    startedAt: "2026-05-03T11:10:00Z",
    durationSeconds: 210,
    status: "open",
    messages: [
      { id: "m1", role: "assistant", content: "Hi! What can I help you with?", timestamp: "2026-05-03T11:10:00Z" },
      { id: "m2", role: "user", content: "Can the bot speak French?", timestamp: "2026-05-03T11:10:30Z" },
      { id: "m3", role: "assistant", content: "Yes! CacaNode supports over 50 languages. The bot will automatically respond in the language the visitor uses.", timestamp: "2026-05-03T11:10:50Z" },
      { id: "m4", role: "user", content: "Does it translate the documents too?", timestamp: "2026-05-03T11:11:20Z" },
      { id: "m5", role: "assistant", content: "Your documents can be uploaded in any language. The AI understands and responds in the visitor's language regardless of the document language.", timestamp: "2026-05-03T11:11:45Z" },
    ],
  },
  {
    id: "conv-7",
    visitorId: "visitor_f8a3b1",
    messageCount: 3,
    startedAt: "2026-05-02T09:15:00Z",
    durationSeconds: 95,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-02T09:15:00Z" },
      { id: "m2", role: "user", content: "How many documents can I upload on the free plan?", timestamp: "2026-05-02T09:15:25Z" },
      { id: "m3", role: "assistant", content: "The Starter plan allows up to 3 documents. Upgrading to Pro gives you 50 documents.", timestamp: "2026-05-02T09:15:45Z" },
    ],
  },
  {
    id: "conv-8",
    visitorId: "visitor_d5e9c2",
    messageCount: 6,
    startedAt: "2026-05-02T13:30:00Z",
    durationSeconds: 280,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hi! What can I help you with?", timestamp: "2026-05-02T13:30:00Z" },
      { id: "m2", role: "user", content: "Is there an API I can use?", timestamp: "2026-05-02T13:30:20Z" },
      { id: "m3", role: "assistant", content: "Yes! We have a REST API available on Pro and Enterprise plans.", timestamp: "2026-05-02T13:30:40Z" },
      { id: "m4", role: "user", content: "Can I see the documentation?", timestamp: "2026-05-02T13:31:10Z" },
      { id: "m5", role: "assistant", content: "You can find the API docs at docs.cacanode.com. Your API key is available in Settings → API Keys.", timestamp: "2026-05-02T13:31:30Z" },
      { id: "m6", role: "user", content: "Perfect, thanks!", timestamp: "2026-05-02T13:32:00Z" },
    ],
  },
  {
    id: "conv-9",
    visitorId: "visitor_e1b7f4",
    messageCount: 4,
    startedAt: "2026-05-01T15:00:00Z",
    durationSeconds: 160,
    status: "open",
    messages: [
      { id: "m1", role: "assistant", content: "Hello! How can I help you today?", timestamp: "2026-05-01T15:00:00Z" },
      { id: "m2", role: "user", content: "What happens when I hit the message limit?", timestamp: "2026-05-01T15:00:30Z" },
      { id: "m3", role: "assistant", content: "The widget will show a message letting visitors know support is temporarily unavailable until the next billing cycle.", timestamp: "2026-05-01T15:00:55Z" },
      { id: "m4", role: "user", content: "Can I upgrade mid-cycle?", timestamp: "2026-05-01T15:01:20Z" },
    ],
  },
  {
    id: "conv-10",
    visitorId: "visitor_9c2d5a",
    messageCount: 2,
    startedAt: "2026-05-01T10:20:00Z",
    durationSeconds: 35,
    status: "resolved",
    messages: [
      { id: "m1", role: "assistant", content: "Hi there! What can I help you with?", timestamp: "2026-05-01T10:20:00Z" },
      { id: "m2", role: "user", content: "Thanks, I found the answer already!", timestamp: "2026-05-01T10:20:20Z" },
    ],
  },
];

export const mockTestimonials: Testimonial[] = [
  {
    quote:
      "CacaNode cut our support ticket volume by 60% in the first month. Setup took less than 10 minutes.",
    name: "Sarah Chen",
    title: "Head of Customer Success",
    company: "Flowbase",
    initials: "SC",
  },
  {
    quote:
      "Our customers get instant answers 24/7. It's like having a support agent that never sleeps and knows everything.",
    name: "Marcus Rivera",
    title: "CTO",
    company: "Stackly",
    initials: "MR",
  },
  {
    quote:
      "We uploaded our entire knowledge base and had a live chatbot the same afternoon. Absolutely worth it.",
    name: "Priya Nair",
    title: "Product Manager",
    company: "Loopkit",
    initials: "PN",
  },
];
