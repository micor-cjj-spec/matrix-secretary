from app.voucher_review_parser import try_parse_voucher_review


def test_parse_query_and_todo_flow() -> None:
    result = try_parse_voucher_review(
        "查询2026年7月未审核凭证，并生成一个审核待办",
        "Asia/Shanghai",
        "trace-test",
    )

    assert result is not None
    assert result.trace_id == "trace-test"
    assert len(result.tasks) == 2
    query, todo = result.tasks
    assert query.action_type == "query_pending_vouchers"
    assert query.skill_name == "matrix_voucher_pending_review"
    assert query.args["period"] == "2026-07"
    assert query.requires_confirmation is False
    assert todo.action_type == "create_todo"
    assert todo.content == "审核 2026-07 未审核凭证"
    assert todo.requires_confirmation is True


def test_parse_query_only() -> None:
    result = try_parse_voucher_review(
        "帮我查一下2026-07待审核凭证",
        "Asia/Shanghai",
    )

    assert result is not None
    assert len(result.tasks) == 1
    assert result.tasks[0].args["period"] == "2026-07"


def test_ignore_unrelated_text() -> None:
    assert try_parse_voucher_review("提醒我明天开会", "Asia/Shanghai") is None
