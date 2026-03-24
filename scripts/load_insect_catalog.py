#!/usr/bin/env python3
"""Load curated insect catalog data into insect_info with repeatable upserts."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Iterable

DEFAULT_SEED_FILE = Path(__file__).resolve().parent / "data" / "insect_catalog_seed.json"
DB_COLUMNS = [
    "id",
    "species_name_cn",
    "species_name_en",
    "order_name",
    "order_name_cn",
    "family_name",
    "family_name_cn",
    "genus_name",
    "genus_name_cn",
    "body_length",
    "body_length_en",
    "distribution",
    "distribution_en",
    "active_season",
    "active_season_en",
    "protection_level",
    "protection_level_en",
    "harm_level",
    "description",
    "description_en",
    "morphology",
    "morphology_en",
    "habits",
    "habits_en",
    "recognition_count",
    "cover_image_url",
]
NEW_COLUMN_DEFINITIONS = {
    "order_name_cn": "VARCHAR(50) DEFAULT NULL COMMENT '目（中文）'",
    "family_name_cn": "VARCHAR(50) DEFAULT NULL COMMENT '科（中文）'",
    "genus_name_cn": "VARCHAR(50) DEFAULT NULL COMMENT '属（中文）'",
    "body_length_en": "VARCHAR(255) DEFAULT NULL COMMENT 'Body length (EN)'",
    "distribution_en": "TEXT COMMENT 'Distribution (EN)'",
    "active_season_en": "VARCHAR(255) DEFAULT NULL COMMENT 'Active season (EN)'",
    "protection_level_en": "VARCHAR(100) DEFAULT NULL COMMENT 'Protection level (EN)'",
    "description_en": "TEXT COMMENT 'Description (EN)'",
    "morphology_en": "TEXT COMMENT 'Morphology (EN)'",
    "habits_en": "TEXT COMMENT 'Habits (EN)'",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed-file", type=Path, default=DEFAULT_SEED_FILE)
    parser.add_argument("--apply", action="store_true", help="Execute SQL against MySQL instead of printing it.")
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "localhost"))
    parser.add_argument("--db-port", default=os.getenv("DB_PORT", "3306"))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "bugsight"))
    parser.add_argument("--db-user", default=os.getenv("DB_USER"))
    parser.add_argument("--db-pass", default=os.getenv("DB_PASS"))
    return parser.parse_args()


def sql_quote(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "\\'")
    return f"'{text}'"


def validate(records: list[dict]) -> None:
    if len(records) != 25:
        raise ValueError(f"expected 25 records, got {len(records)}")
    ids = [record["class_id"] for record in records]
    if len(set(ids)) != len(ids):
        raise ValueError("duplicate class_id found in seed file")


def mysql_base_command(args: argparse.Namespace) -> list[str]:
    if not args.db_user or args.db_pass is None:
        raise ValueError("DB_USER and DB_PASS are required when using --apply")
    return [
        "mysql",
        f"-h{args.db_host}",
        f"-P{args.db_port}",
        f"-u{args.db_user}",
        args.db_name,
    ]


def run_mysql(sql: str, args: argparse.Namespace, *, capture_output: bool = False) -> str:
    env = os.environ.copy()
    env["MYSQL_PWD"] = args.db_pass
    command = mysql_base_command(args)
    completed = subprocess.run(
        command,
        input=sql,
        text=True,
        check=True,
        env=env,
        capture_output=capture_output,
    )
    return completed.stdout if capture_output else ""


def ensure_schema(args: argparse.Namespace) -> None:
    existing_raw = run_mysql(
        (
            "SELECT COLUMN_NAME "
            "FROM INFORMATION_SCHEMA.COLUMNS "
            f"WHERE TABLE_SCHEMA = '{args.db_name}' AND TABLE_NAME = 'insect_info';"
        ),
        args,
        capture_output=True,
    )
    existing = {line.strip() for line in existing_raw.splitlines() if line.strip()}
    missing = [name for name in NEW_COLUMN_DEFINITIONS if name not in existing]
    if not missing:
        return

    alter_sql = (
        "ALTER TABLE insect_info\n" +
        ",\n".join(f"ADD COLUMN {name} {NEW_COLUMN_DEFINITIONS[name]}" for name in missing) +
        ";\n"
    )
    run_mysql(alter_sql, args)


def to_row(record: dict) -> dict:
    return {
        "id": record["class_id"],
        "species_name_cn": record["species_name_cn"],
        "species_name_en": record["species_name_en"],
        "order_name": record["order_name"],
        "order_name_cn": record.get("order_name_cn") or "",
        "family_name": record["family_name"],
        "family_name_cn": record.get("family_name_cn") or "",
        "genus_name": record.get("genus_name") or "",
        "genus_name_cn": record.get("genus_name_cn") or "",
        "body_length": record.get("body_length") or "",
        "body_length_en": record.get("body_length_en") or "",
        "distribution": record.get("distribution") or "",
        "distribution_en": record.get("distribution_en") or "",
        "active_season": record.get("active_season") or "",
        "active_season_en": record.get("active_season_en") or "",
        "protection_level": record.get("protection_level") or "未评估",
        "protection_level_en": record.get("protection_level_en") or "Not evaluated",
        "harm_level": record.get("harm_level", 2),
        "description": record.get("description") or "",
        "description_en": record.get("description_en") or "",
        "morphology": record.get("morphology") or "",
        "morphology_en": record.get("morphology_en") or "",
        "habits": record.get("habits") or "",
        "habits_en": record.get("habits_en") or "",
        "recognition_count": record.get("recognition_count", 0),
        "cover_image_url": record.get("cover_image_url") or "",
    }


def build_sql(records: Iterable[dict]) -> str:
    header = [
        "SET NAMES utf8mb4;",
        "SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'NO_AUTO_VALUE_ON_ZERO');",
    ]
    statements = []
    update_columns = [column for column in DB_COLUMNS if column not in {"id", "recognition_count"}]
    for record in records:
        row = to_row(record)
        values = ", ".join(sql_quote(row[column]) for column in DB_COLUMNS)
        update_clause = ",\n    ".join(f"{column}=VALUES({column})" for column in update_columns)
        statements.append(
            "INSERT INTO insect_info (" + ", ".join(DB_COLUMNS) + ")\n"
            f"VALUES ({values})\n"
            "ON DUPLICATE KEY UPDATE\n    " + update_clause + ";"
        )
    return "\n\n".join(header + statements) + "\n"


def main() -> int:
    args = parse_args()
    records = json.loads(args.seed_file.read_text())
    validate(records)
    sql = build_sql(records)

    if args.apply:
        ensure_schema(args)
        run_mysql(sql, args)
        print(f"Applied {len(records)} catalog records to insect_info from {args.seed_file}")
    else:
        print(sql)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"mysql command failed: {exc}", file=sys.stderr)
        raise SystemExit(exc.returncode)
    except Exception as exc:  # pragma: no cover - CLI guard
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
