from app.month_end_close_parser import try_parse_month_end_close


def test_parse_month_end_close_only():
    result = try_parse_month_end_close(
        "检查本月是否满足结账条件，并列出阻塞项",
        "Asia/Shanghai",
        "trace-test",
    )

    assert result is not None
    assert result.trace_id == "trace-test"
    assert len(result.tasks) == 1
    assert result.tasks[0].action_type == "check_month_end_close"
    assert result.tasks[0].skill_name == "matrix_month_end_close_check"
    assert result.tasks[0].requires_confirmation is False


def test_parse_month_end_close_with_todo_and_org():
    result = try_parse_month_end_close(
        "检查 2026-07 业务单元 1001 是否可以结账，并生成一个处理待办",
        "Asia/Shanghai",
    )

    assert result is not None
    assert len(result.tasks) == 2
    assert result.tasks[0].args["period"] == "2026-07"
    assert result.tasks[0].args["forg"] == 1001
    assert result.tasks[1].action_type == "create_todo"
    assert result.tasks[1].requires_confirmation is True


def test_ignore_unrelated_text():
    assert try_parse_month_end_close("提醒我明天开会", "Asia/Shanghai") is None
