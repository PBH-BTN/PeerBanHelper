#!/usr/bin/env python3
"""Compare PeerBanHelper behavior captures using a strict JSONL contract."""

import argparse
import json
import math
import subprocess
import sys
from pathlib import Path


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key {key!r}")
        result[key] = value
    return result


def finite_float(raw):
    value = float(raw)
    if not math.isfinite(value):
        raise ValueError(f"Non-finite number {raw}")
    return value


def records(text, label, *, output=False):
    result = {}
    for line_number, line in enumerate(text.split("\n"), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(
                line, object_pairs_hook=unique_object,
                parse_float=finite_float, parse_constant=finite_float,
            )
            if not isinstance(row, dict) or not isinstance(row.get("id"), str) or not row["id"]:
                raise ValueError("A nonempty string id is required")
            if row["id"] in result:
                raise ValueError(f"Duplicate record {row['id']!r}")
            if output:
                if set(row) not in ({"id", "value"}, {"id", "error"}):
                    raise ValueError("Expected id plus exactly one of value or error")
                if "error" in row:
                    error = row["error"]
                    if (not isinstance(error, dict) or set(error) != {"type", "message"}
                            or not all(isinstance(value, str) for value in error.values())):
                        raise ValueError("Error requires string type and message")
            result[row["id"]] = row
        except ValueError as error:
            raise ValueError(f"{label}, line {line_number}: {error}") from error
    return result


def differences(reference, candidate, path=""):
    if type(reference) is type(candidate):
        if isinstance(reference, dict):
            for key in sorted(reference.keys() | candidate.keys()):
                pointer = f"{path}/{key.replace('~', '~0').replace('/', '~1')}"
                if key in reference and key in candidate:
                    yield from differences(reference[key], candidate[key], pointer)
                elif key in reference:
                    yield {"path": pointer, "missing": "candidate", "reference": reference[key]}
                else:
                    yield {"path": pointer, "missing": "reference", "candidate": candidate[key]}
            return
        if isinstance(reference, list) and len(reference) == len(candidate):
            for index, (expected, actual) in enumerate(zip(reference, candidate)):
                yield from differences(expected, actual, f"{path}/{index}")
            return
        if reference == candidate:
            return
    yield {"path": path, "reference": reference, "candidate": candidate}


def compare(corpus, reference, candidate):
    unexpected = {"reference": sorted(reference.keys() - corpus.keys()),
                  "candidate": sorted(candidate.keys() - corpus.keys())}
    has_reference_values = any("value" in reference.get(case, {}) for case in corpus)
    counts = {"equivalent": 0, "divergent": 0, "incomparable": 0}
    results = []
    for case in corpus:
        result = {"id": case}
        missing = [side for side, rows in (("reference", reference), ("candidate", candidate))
                   if case not in rows]
        if missing:
            result.update(verdict="incomparable", missing=missing)
        elif not has_reference_values:
            result.update(verdict="incomparable", reason="reference returned no values")
        else:
            diff = list(differences(reference[case], candidate[case]))
            if diff:
                result.update(verdict="divergent", differences=diff)
            else:
                result.update(verdict="equivalent")
        counts[result["verdict"]] += 1
        results.append(result)
    if counts["divergent"]:
        verdict = "divergent"
    elif counts["incomparable"] or any(unexpected.values()) or not corpus:
        verdict = "incomparable"
    else:
        verdict = "equivalent"
    return {"verdict": verdict, "counts": counts, "unexpected": unexpected, "results": results}


def self_test():
    decision = {"decision": "BAN", "duration": 10, "events": ["ban"],
                "ip": "192.0.2.1", "port": 1}
    expected = {
        "valid": {"id": "valid", "value": decision},
        "invalid": {"id": "invalid", "error": {"type": "bad_input", "message": "Invalid port"}},
    }
    corpus = {case: {"id": case} for case in expected}
    mutations = {
        "off-by-one": {"duration": 11},
        "dropped-branch": {"decision": "NO_ACTION"},
        "swapped-inputs": {"ip": decision["port"], "port": decision["ip"]},
        "wrong-type-coercion": {"duration": 10.0, "port": True},
        "duplicate-downloader-request": {"events": ["ban", "ban"]},
    }
    found = {}
    for name, change in mutations.items():
        record = json.dumps({"id": "valid", "value": decision | change})
        candidate = expected | records(record, name, output=True)
        found[name] = compare(corpus, expected, candidate)["verdict"] == "divergent"
    swallowed = expected | records('{"id":"invalid","value":null}', "swallowed-error", output=True)
    found["swallowed-error"] = compare(corpus, expected, swallowed)["verdict"] == "divergent"
    reordered = json.dumps(expected["valid"], sort_keys=True)
    candidate = expected | records(reordered, "reordered keys", output=True)
    found["object-order-equivalent"] = compare(corpus, expected, candidate)["verdict"] == "equivalent"
    found["missing-output-incomparable"] = compare(corpus, expected, {})["verdict"] == "incomparable"
    errors = {"invalid": expected["invalid"]}
    found["all-error-reference-incomparable"] = compare(errors, errors, errors)["verdict"] == "incomparable"
    valid_corpus = {"valid": corpus["valid"]}
    found["unexpected-case-incomparable"] = compare(valid_corpus, expected, expected)["verdict"] == "incomparable"
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="mode", required=True)
    subcommands.add_parser("self-test", help="Check divergence and incomparable cases")
    for mode in ("compare", "judge"):
        command = subcommands.add_parser(mode)
        command.add_argument("--corpus", type=Path, required=True)
        command.add_argument("--reference", type=Path, required=True)
        if mode == "compare":
            command.add_argument("--candidate", type=Path, required=True)
        else:
            command.add_argument("--timeout", type=float, default=30)
            command.add_argument("command", nargs=argparse.REMAINDER)
    options = parser.parse_args()
    if options.mode == "self-test":
        report = self_test()
        print(json.dumps(report, indent=2, allow_nan=False))
        return 0 if all(report.values()) else 1

    try:
        corpus_text = options.corpus.read_text(encoding="utf-8")
        corpus = records(corpus_text, "corpus")
        reference = records(options.reference.read_text(encoding="utf-8"), "reference", output=True)
        if options.mode == "compare":
            candidate_text = options.candidate.read_text(encoding="utf-8")
        else:
            command = options.command
            if command[:1] == ["--"]:
                command = command[1:]
            if not command or not math.isfinite(options.timeout) or options.timeout <= 0:
                parser.error("judge requires a command and a positive finite timeout")
            child = subprocess.run(command, input=corpus_text, capture_output=True,
                                   encoding="utf-8", timeout=options.timeout, check=True)
            candidate_text = child.stdout
        candidate = records(candidate_text, "candidate", output=True)
        report = compare(corpus, reference, candidate)
    except (OSError, ValueError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
        print(json.dumps({"verdict": "incomparable", "error": str(error)}, indent=2))
        return 3
    print(json.dumps(report, indent=2, ensure_ascii=True, allow_nan=False))
    return {"equivalent": 0, "divergent": 2, "incomparable": 3}[report["verdict"]]


if __name__ == "__main__":
    sys.exit(main())
