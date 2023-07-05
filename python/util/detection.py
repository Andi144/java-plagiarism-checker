import pandas as pd

metrics = [
    "ASTDiffMetric",
    "RenamedASTDiffMetric",
    "ASTCountDiffMetric",
    "JaccardMetric",
    "RenamedJaccardMetric",
    "JaroWinklerMetric",
    "RenamedJaroWinklerMetric"
]
metrics_without_count = [m for m in metrics if m != "ASTCountDiffMetric"]


def add_basic_plagiarism_rules(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    # This rule effectively represents a 1:1 copy. It is not really necessary, as rule2 would already match (but it is
    # useful as an additional distinction for the visualization afterward).
    df["rule0"] = df[metrics].sum(axis=1) == 0
    # For the following rules, it can only be at least considered as plag if the count diff is small enough
    small_count_diff = df["ASTCountDiffMetric"] < 0.2
    # If the sum of all metrics is that small, we are confident that it is a plag
    df["rule1"] = small_count_diff & (df[metrics].sum(axis=1) < 0.05)
    # If at least 3 metrics are exactly 0, we are confident that it is a plag
    df["rule2"] = small_count_diff & ((df[metrics_without_count] == 0).sum(axis=1) >= 3)
    # If none of the above match, use this metric and threshold as final decider
    df["rule3"] = small_count_diff & (df["RenamedASTDiffMetric"] < 0.01)
    return df
