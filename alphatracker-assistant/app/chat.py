import logging
from datetime import date
from typing import Annotated, Any, Callable, Iterable

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from app.agent import Agent, AgentError, AgentResult
from app.config import Settings
from app.llm import LLM, AssistantNotConfigured, LLMError, Message
from app.periods import today_in
from app.prompts import build_context, build_system_prompt
from app.spring_client import SpringAuthError, SpringUnavailable
from app.tools import ToolRunner

logger = logging.getLogger("assistant.chat")

# The client owns the conversation (the service is stateless), so the request
# carries it. Both limits bound what one request can cost in tokens.
MAX_HISTORY_TURNS = 20
MAX_MESSAGE_CHARS = 2000
MAX_HISTORY_ITEM_CHARS = 4000


class HistoryTurn(BaseModel):
    # Only user/assistant: a client can't smuggle in a "system" turn.
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=MAX_HISTORY_ITEM_CHARS)


class ChatRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    message: str = Field(min_length=1, max_length=MAX_MESSAGE_CHARS)
    history: list[HistoryTurn] = Field(default_factory=list, max_length=100)


class ToolCallOut(BaseModel):
    name: str
    args: dict[str, Any]
    status: str  # ok | error | repeat


class ChatResponse(BaseModel):
    answer: str
    tool_calls: list[ToolCallOut]


def build_chat_router(settings: Settings, spring: Any, get_llm: Callable[[], LLM]) -> APIRouter:
    router = APIRouter()
    runner = ToolRunner(spring, today=lambda: today_in(settings.timezone))

    # A plain `def` on purpose: FastAPI runs it in a worker thread, and the
    # loop underneath is synchronous (see CONTEXT.md on sync vs async).
    @router.post("/chat", response_model=ChatResponse)
    def chat(request: ChatRequest, authorization: Annotated[str | None, Header()] = None) -> ChatResponse:
        token = _bearer_token(authorization)

        try:
            result = answer_question(
                settings=settings, spring=spring, runner=runner, get_llm=get_llm, token=token,
                message=request.message,
                history=to_messages((t.role, t.content) for t in request.history),
                today=today_in(settings.timezone),
            )
        except AssistantNotConfigured:
            raise HTTPException(503, "The assistant isn't configured on the server.") from None
        except SpringAuthError:
            raise HTTPException(401, "Your session has expired or is invalid. Please log in again.",
                                headers={"WWW-Authenticate": "Bearer"}) from None
        except SpringUnavailable:
            raise HTTPException(503, "The trading data service is unavailable right now. Try again shortly.") from None
        except LLMError as exc:
            # Log the class of failure only; never the request, the token, or message text.
            logger.warning("model call failed (retryable=%s)", exc.retryable)
            status = 503 if exc.retryable else 502
            raise HTTPException(status, "The assistant is unavailable right now. Try again shortly.") from None
        except AgentError:
            raise HTTPException(502, "The assistant couldn't produce an answer. Try rephrasing.") from None

        if result.unverified:
            logger.warning("answer withheld: %d unsupported figure(s)", len(result.unverified))
        logger.info("chat answered; tools=%s", [t.name for t in result.tool_calls])
        return ChatResponse(
            answer=result.answer,
            tool_calls=[ToolCallOut(name=t.name, args=t.args, status=t.status) for t in result.tool_calls],
        )

    return router


def answer_question(
    *,
    settings: Settings,
    spring: Any,
    runner: ToolRunner,
    get_llm: Callable[[], LLM],
    token: str,
    message: str,
    history: list[Message],
    today: date,
) -> AgentResult:
    """The whole path from a question to a checked answer.

    Shared by the /chat endpoint and the eval harness on purpose: the evals then
    exercise exactly what production runs (same preflight, same prompt, same loop)
    instead of a lookalike that could drift away from it.
    """
    # Preflight. It proves the JWT is good BEFORE any model call is paid for (a bad
    # token costs one cheap GET, not a Gemini request), and the result preloads the
    # trader's accounts into the prompt so "my Apex account" resolves.
    accounts = spring.get("/accounts", token)
    llm = get_llm()
    context = build_context(today, settings.timezone, accounts if isinstance(accounts, list) else None)
    return Agent(llm, runner).run(
        system=build_system_prompt(context),
        context_text=context,
        history=history,
        user_message=message,
        token=token,
    )


def _bearer_token(authorization: str | None) -> str:
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(401, "Missing bearer token.", headers={"WWW-Authenticate": "Bearer"})
    return token.strip()


def to_messages(turns: Iterable[tuple[str, str]]) -> list[Message]:
    """(role, content) pairs -> the last MAX_HISTORY_TURNS as Messages."""
    recent = list(turns)[-MAX_HISTORY_TURNS:]
    # Cutting to the last N can leave the conversation starting on an assistant
    # turn, which would confuse the model; start from the first user turn instead.
    while recent and recent[0][0] != "user":
        recent = recent[1:]
    return [Message(role, text=content) for role, content in recent]  # type: ignore[arg-type]
