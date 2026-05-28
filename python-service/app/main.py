from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

from app.llm_parser import parse_text_with_llm_with_diagnostics
from app.parser import parse_text
from app.postprocess import normalize_parse_response
from app.schemas import ParseRequest, ParseResponse


BASE_DIR = Path(__file__).resolve().parent

app = FastAPI(
    title="AI Secretary Task Parser Demo",
    description="将自然语言拆分为可确认、可调度、可执行的 AI 秘书任务计划。",
    version="0.1.0",
)

app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")


@app.get("/", response_class=HTMLResponse)
async def index() -> str:
    return (BASE_DIR / "static" / "index.html").read_text(encoding="utf-8")


@app.post("/api/v1/semantic/parse", response_model=ParseResponse)
async def parse_semantic_task(request: ParseRequest) -> ParseResponse:
    llm_result = await parse_text_with_llm_with_diagnostics(request.text, request.timezone, request.trace_id)
    if llm_result.response:
        return safe_normalize_parse_response(llm_result.response, parser_source="llm")
    fallback = parse_text(request.text, request.timezone, request.trace_id, allow_complex=False)
    warnings = [*fallback.warnings]
    if llm_result.warning:
        warnings.insert(0, llm_result.warning)
    fallback = fallback.model_copy(update={"warnings": warnings})
    return safe_normalize_parse_response(fallback, parser_source="fallback", parse_warning=llm_result.warning)


def safe_normalize_parse_response(
    response: ParseResponse,
    parser_source: str | None = None,
    parse_warning: str | None = None,
) -> ParseResponse:
    try:
        return normalize_parse_response(response, parser_source=parser_source, parse_warning=parse_warning)
    except Exception as ex:
        warnings = [*response.warnings, f"后处理未完全适配当前自定义 Skill，已返回原始解析结果: {ex.__class__.__name__}"]
        return response.model_copy(update={"warnings": warnings})


@app.get("/api/v1/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
