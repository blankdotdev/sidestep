import json
import os
from collections import defaultdict

FINDINGS_FILE = "findings.json"

def load_findings():
    if not os.path.exists(FINDINGS_FILE):
        print(f"Error: {FINDINGS_FILE} not found. Run 'gh api ... > {FINDINGS_FILE}' first.")
        return []
    
    findings = []
    with open(FINDINGS_FILE, "r") as f:
        for line in f:
            try:
                findings.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return findings

def group_by_rule(findings):
    groups = defaultdict(list)
    for f in findings:
        groups[f['rule']].append(f)
    return groups

def group_by_file(findings):
    groups = defaultdict(list)
    for f in findings:
        groups[f['location']].append(f)
    return groups

def print_summary(findings):
    print(f"Total Findings: {len(findings)}")
    print("-" * 30)
    
    rule_groups = group_by_rule(findings)
    print("\nTop finding types:")
    sorted_rules = sorted(rule_groups.items(), key=lambda x: len(x[1]), reverse=True)
    for rule, items in sorted_rules[:10]:
        print(f" - {rule}: {len(items)}")

def list_batches(findings):
    rule_groups = group_by_rule(findings)
    sorted_rules = sorted(rule_groups.items(), key=lambda x: len(x[1]), reverse=True)
    
    print("\nAvailable Batches (by rule):")
    for i, (rule, items) in enumerate(sorted_rules):
        print(f"[{i}] {rule} ({len(items)} findings)")

def list_file_findings(findings, file_path, rule=None):
    print(f"\nFindings in {file_path}:")
    for f in findings:
        if f['location'] == file_path:
            if rule and rule not in f['rule']:
                continue
            print(f" - #{f['number']}: {f['rule']}")

if __name__ == "__main__":
    findings = load_findings()
    if findings:
        import sys
        if "--list" in sys.argv:
            list_batches(findings)
        elif "--file" in sys.argv:
            idx = sys.argv.index("--file")
            file_path = sys.argv[idx+1]
            rule = None
            if "--rule" in sys.argv:
                ridx = sys.argv.index("--rule")
                rule = sys.argv[ridx+1]
            list_file_findings(findings, file_path, rule)
        else:
            print_summary(findings)
