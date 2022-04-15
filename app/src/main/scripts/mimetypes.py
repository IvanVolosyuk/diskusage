#!/usr/bin/env python

# Regenerate mimetypes

import fileinput
from collections import defaultdict

mapping = {}

for line in fileinput.input():
    tokens = line.split()

    if len(tokens) < 2: continue
    mime = tokens[0]

    if mime[0] == '#': continue

    for ext in tokens[1:]:
        if ext not in mapping:
            mapping[ext] = mime
        # else:
        #     if mapping[ext] != mime:
        #         print("Changed mime:", mapping[ext], " vs", mime, " for ", ext)

mimes = defaultdict(list)

for (ext, mime) in mapping.items():
    mimes[mime].append(ext)

for (mime, exts) in mimes.items():
    print(mime)
    for ext in exts:
        print(ext)
    print()

