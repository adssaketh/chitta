#!/usr/bin/env python3
"""Regenerate ChittaIcons.kt from Phosphor Icons (MIT), Regular weight.

    npm pack @phosphor-icons/core && tar xzf phosphor-icons-core-*.tgz
    python3 tools/gen_icons.py package/assets/regular

Edit ICONS to add an icon (Kotlin name, Phosphor file name without .svg), rerun, commit the result.
Regular weight is a 16/256 stroke = 1.5 dp at 24 dp, which is the master prompt's icon spec.
"""
import os, re, sys

ICONS = [
    ("Home", "house"), ("Ledger", "book-open"), ("Plan", "target"), ("Insights", "chart-bar"), ("More", "dots-three"),
    ("Plus", "plus"), ("Eye", "eye"), ("EyeSlash", "eye-slash"), ("CreditCard", "credit-card"), ("Bank", "bank"),
    ("Wallet", "wallet"), ("Search", "magnifying-glass"), ("Camera", "camera"), ("ArrowLeft", "arrow-left"),
    ("Check", "check"), ("Close", "x"), ("Backspace", "backspace"), ("CaretRight", "caret-right"),
    ("CaretDown", "caret-down"), ("Trash", "trash"), ("Pencil", "pencil-simple"), ("Share", "share-network"),
    ("Copy", "copy"), ("Lock", "lock"), ("Fingerprint", "fingerprint"), ("Bell", "bell"), ("Gear", "gear"),
    ("Tag", "tag"), ("Repeat", "repeat"), ("Transfer", "arrows-left-right"), ("Calendar", "calendar-blank"),
    ("Receipt", "receipt"), ("Info", "info"), ("Warning", "warning"), ("Users", "users"), ("Filter", "funnel"),
    ("Download", "download-simple"), ("Upload", "upload-simple"), ("Undo", "arrow-counter-clockwise"),
]

OUT = "core/design/src/main/java/com/mansaketh/chitta/core/design/icons/ChittaIcons.kt"

def main(src):
    body = []
    for kname, fname in ICONS:
        svg = open(os.path.join(src, fname + ".svg")).read()
        paths = re.findall(r'<path d="([^"]+)"', svg)
        assert len(paths) == 1 and 'viewBox="0 0 256 256"' in svg, fname
        body.append((kname, fname, paths[0]))
    lines = [
        "package com.mansaketh.chitta.core.design.icons", "",
        "import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.vector.ImageVector", "import androidx.compose.ui.graphics.vector.PathParser",
        "import androidx.compose.ui.unit.dp", "",
        "/*", " * Generated from Phosphor Icons 2.1.1, Regular weight (MIT, see licenses/phosphor-LICENSE).",
        " * Regular weight is a 16/256 stroke, which is 1.5 dp at the 24 dp size (master section 3).",
        " * Each icon is one filled path in a 256 x 256 viewport. Tint with LocalContentColor.",
        " * Regenerate with tools/gen_icons.py; do not edit by hand.", " */", "",
        "private fun phosphor(name: String, pathData: String): ImageVector =",
        "    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)",
        "        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))",
        "        .build()", "", "object ChittaIcons {",
    ]
    for k, f, d in body:
        lines += [f"    /** phosphor: {f} */", f'    val {k}: ImageVector by lazy {{ phosphor("{k}", "{d}") }}', ""]
    lines += ["    /** Every icon, for the component gallery. */", "    val all: List<Pair<String, ImageVector>> by lazy {", "        listOf("]
    lines += [f'            "{k}" to {k},' for k, _, _ in body]
    lines += ["        )", "    }", "}"]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w").write("\n".join(lines) + "\n")
    print(len(body), "icons ->", OUT)

if __name__ == "__main__":
    main(sys.argv[1])
