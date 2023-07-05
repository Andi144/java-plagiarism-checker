import os
import re
from collections.abc import Iterable

import pandas as pd


def extract_student(dir_path: str) -> str:
    dir_name = os.path.basename(dir_path)
    mo = re.search(r"[kK]\d{6,8}", dir_name)
    if mo is not None:
        matr_id = mo.group()
        return f"{matr_id:0>8}"
    if "_" in dir_name:
        # Assumed format: <full name>_<internal ID>_assignsubmission_file_
        return dir_name.split("_")[0]
    return dir_path


def read_comparison_csv(comparison_file, excluded_types: Iterable[str] = ()):
    df = pd.read_csv(comparison_file)
    df = df[~df["type1"].isin(excluded_types) & ~df["type2"].isin(excluded_types)]
    df["student1"] = df["folder1"].apply(extract_student)
    df["student2"] = df["folder2"].apply(extract_student)
    return df[list(df.columns)[-2:] + list(df.columns)[:-2]]  # Reorder columns for better readability
