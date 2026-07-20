import re

FALLBACK_TENANT_NAME = "this organization"

_CONVERSATIONAL_PATTERNS: tuple[tuple[str, str, re.Pattern[str]], ...] = (
    (
        "identity",
        "vi",
        re.compile(
            r"(?:bạn|ban)\s+(?:là|la)\s+ai|(?:bạn|ban)\s+tên\s+gì|"
            r"(?:bạn|ban)\s+(?:tên|ten)\s+(?:gì|gi)|đây\s+là\s+ai|day\s+la\s+ai"
        ),
    ),
    (
        "identity",
        "en",
        re.compile(
            r"who\s+are\s+you|what\s+are\s+you|what(?:'s|\s+is)\s+your\s+name|"
            r"tell\s+me\s+about\s+yourself"
        ),
    ),
    (
        "capabilities",
        "vi",
        re.compile(
            r"(?:bạn|ban)\s+có\s+thể\s+làm\s+gì|(?:bạn|ban)\s+co\s+the\s+lam\s+gi|"
            r"(?:bạn|ban)\s+có\s+thể\s+giúp\s+gì|(?:bạn|ban)\s+co\s+the\s+giup\s+gi"
        ),
    ),
    (
        "capabilities",
        "en",
        re.compile(r"what\s+can\s+you\s+do|how\s+can\s+you\s+help(?:\s+me)?"),
    ),
    (
        "greeting",
        "vi",
        re.compile(
            r"(?:xin\s+chào|xin\s+chao|chào|chao|alo)"
            r"(?:\s+(?:bạn|ban|anh|chị|chi|mọi\s+người|moi\s+nguoi))?"
        ),
    ),
    (
        "greeting",
        "en",
        re.compile(
            r"(?:hello|hi|hey|good\s+morning|good\s+afternoon|good\s+evening)"
            r"(?:\s+(?:there|everyone))?"
        ),
    ),
    (
        "smalltalk",
        "vi",
        re.compile(r"(?:bạn|ban)\s+(?:có\s+)?khỏe\s+không|(?:bạn|ban)\s+(?:co\s+)?khoe\s+khong"),
    ),
    (
        "smalltalk",
        "en",
        re.compile(r"how\s+are\s+you|how(?:'s|\s+is)\s+it\s+going"),
    ),
    (
        "thanks",
        "vi",
        re.compile(r"(?:cảm\s+ơn|cam\s+on)(?:\s+(?:bạn|ban|anh|chị|chi))?"),
    ),
    (
        "thanks",
        "en",
        re.compile(r"thanks(?:\s+a\s+lot)?|thank\s+you(?:\s+very\s+much)?"),
    ),
    (
        "farewell",
        "vi",
        re.compile(r"tạm\s+biệt|tam\s+biet|hẹn\s+gặp\s+lại|hen\s+gap\s+lai"),
    ),
    (
        "farewell",
        "en",
        re.compile(r"bye|goodbye|see\s+you(?:\s+later)?"),
    ),
)


def normalized_tenant_name(tenant_name: str) -> str:
    return " ".join(tenant_name.split()) or FALLBACK_TENANT_NAME


def default_customer_answer_prompt(tenant_name: str) -> str:
    display_name = normalized_tenant_name(tenant_name)
    return (
        f"You are the customer-facing assistant for {display_name}. "
        f"Always identify and represent the organization as {display_name}. "
        "Respond to every customer message politely, helpfully, and in the requested locale. "
        "Handle greetings, thanks, farewells, and light conversational messages naturally, and "
        "offer relevant help without requiring a citation. "
        "For questions about the products, services, policies, procedures, or other "
        f"organization-specific facts of {display_name}, answer only from supplied tenant sources "
        "and cite the relevant sources. If the sources do not contain enough information, say so "
        "politely and suggest a safe next step instead of guessing. Never fabricate "
        "tenant-specific facts, claim an action was completed when it was not, or expose "
        "information belonging to another tenant."
    )


DEFAULT_CUSTOMER_ANSWER_PROMPT = default_customer_answer_prompt(FALLBACK_TENANT_NAME)


def conversational_customer_answer(query: str, tenant_name: str) -> str | None:
    normalized_query = " ".join(query.casefold().split()).strip(" \t\r\n.,!?;:…")
    if not normalized_query:
        return None
    matched: tuple[str, str] | None = None
    for intent, language, pattern in _CONVERSATIONAL_PATTERNS:
        if pattern.fullmatch(normalized_query):
            matched = intent, language
            break
    if matched is None:
        return None

    intent, language = matched
    display_name = normalized_tenant_name(tenant_name)
    if language == "vi":
        responses = {
            "identity": (
                f"Tôi là trợ lý hỗ trợ khách hàng của {display_name}. "
                "Tôi luôn sẵn sàng trả lời câu hỏi và hỗ trợ bạn."
            ),
            "capabilities": (
                f"Tôi có thể trả lời câu hỏi dựa trên thông tin hiện có của {display_name}, "
                "giải thích sản phẩm, dịch vụ và chính sách, hoặc chuẩn bị bản nháp yêu cầu hỗ trợ "
                "khi bạn đề nghị."
            ),
            "greeting": (
                f"Xin chào! Tôi là trợ lý hỗ trợ khách hàng của {display_name}. "
                "Tôi có thể giúp gì cho bạn hôm nay?"
            ),
            "smalltalk": (
                f"Cảm ơn bạn, tôi vẫn ổn và luôn sẵn sàng hỗ trợ khách hàng của {display_name}. "
                "Tôi có thể giúp gì cho bạn?"
            ),
            "thanks": "Rất vui được hỗ trợ bạn! Nếu cần thêm điều gì, bạn cứ cho tôi biết nhé.",
            "farewell": "Tạm biệt! Chúc bạn một ngày tốt lành.",
        }
    else:
        responses = {
            "identity": (
                f"I'm the customer support assistant for {display_name}. "
                "I'm here to answer questions and help you politely."
            ),
            "capabilities": (
                f"I can answer questions using {display_name}'s available information, explain "
                "products, services, and policies, or prepare a support-request draft when asked."
            ),
            "greeting": (
                f"Hello! I'm the customer support assistant for {display_name}. "
                "How can I help you today?"
            ),
            "smalltalk": (
                f"I'm doing well, thank you, and I'm ready to help customers of {display_name}. "
                "What can I help you with?"
            ),
            "thanks": (
                "You're welcome! Please let me know if there is anything else I can help with."
            ),
            "farewell": "Goodbye! Have a great day.",
        }
    return responses[intent]
