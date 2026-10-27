"""Tests for the GitHub Actions workflow configuration.

These check the properties the specification demands of the automation:
the schedule, the permissions, the concurrency group, the ``[skip netlify]``
commit marker, the single-workflow publication and the "no empty commit"
behaviour.
"""

from __future__ import annotations

from pathlib import Path

import pytest

yaml = pytest.importorskip("yaml")

REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"


def load_workflow(name: str) -> dict:
    path = WORKFLOW_DIR / name
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


@pytest.fixture(scope="module")
def workflow() -> dict:
    return load_workflow("update-apod.yml")


def _steps(workflow: dict) -> list[dict]:
    return workflow["jobs"]["update"]["steps"]


def _run_text(workflow: dict) -> str:
    return "\n".join(str(step.get("run", "")) for step in _steps(workflow))


def test_workflow_file_exists():
    assert (WORKFLOW_DIR / "update-apod.yml").is_file()


def test_schedule_probes_the_publication_window(workflow):
    # PyYAML parses the unquoted cron value as a string.
    crons = [entry["cron"] for entry in workflow[True]["schedule"] if isinstance(entry, dict)]
    assert "17 5,8,11,14,17 * * *" in crons


def test_manual_runs_are_supported(workflow):
    assert "workflow_dispatch" in workflow[True]


def test_concurrency_group_is_set_and_does_not_cancel(workflow):
    concurrency = workflow["concurrency"]
    assert concurrency["group"]
    assert concurrency.get("cancel-in-progress") is False


def test_permissions_are_minimal(workflow):
    permissions = workflow["permissions"]
    assert permissions["contents"] == "write"
    assert permissions["pages"] == "write"
    assert permissions["id-token"] == "write"
    # Nothing else may be granted.
    assert set(permissions) == {"contents", "pages", "id-token"}


def test_commit_message_contains_skip_netlify(workflow):
    run = _run_text(workflow)
    assert "[skip netlify]" in run
    assert "git commit -m" in run


def test_only_data_files_are_staged(workflow):
    run = _run_text(workflow)
    assert "git add data/" in run
    # The website and the Android app must never be committed by the bot.
    assert "git add web/" not in run
    assert "git add android/" not in run


def test_no_empty_commit_is_created(workflow):
    run = _run_text(workflow)
    assert "git diff --cached --quiet" in run


def test_pages_are_published_from_the_same_workflow(workflow):
    uses = [str(step.get("uses", "")) for step in _steps(workflow)]
    assert any(item.startswith("actions/upload-pages-artifact") for item in uses)
    assert any(item.startswith("actions/deploy-pages") for item in uses)


def test_the_artifact_is_validated_before_it_is_published(workflow):
    names = [str(step.get("name", "")) for step in _steps(workflow)]
    validate_index = next(i for i, name in enumerate(names) if "Validate" in name)
    deploy_index = next(i for i, step in enumerate(_steps(workflow)) if str(step.get("uses", "")).startswith("actions/deploy-pages"))
    assert validate_index < deploy_index


def test_no_api_key_is_used(workflow):
    """The current NASA endpoint needs no key, so none is required."""
    text = (WORKFLOW_DIR / "update-apod.yml").read_text(encoding="utf-8")
    assert "api_key" not in text
    assert "NASA_API_KEY" not in text


def test_ci_workflow_runs_the_tests():
    ci = load_workflow("ci.yml")
    assert "data" in ci["jobs"]
    assert "website" in ci["jobs"]
    assert "android" in ci["jobs"]
    run = "\n".join(str(step.get("run", "")) for job in ci["jobs"].values() for step in job["steps"])
    assert "pytest" in run
    assert "npm run build" in run
    assert "assembleDebug" in run
