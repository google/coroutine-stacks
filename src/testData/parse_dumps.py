# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import pygtrie as trie
from dataclasses import dataclass

DUMP_DIR = "dumps/"
OUT_DIR = "outs/"

@dataclass
class Coroutine:
	state: str
	stack_trace: list

def parse_dump(file_name: str) -> list[Coroutine]:
	coroutines = []
	with open(file_name, "r") as f:
		current_coroutine = None
		for line in f.readlines():
			line = line.strip()
			if not line:
				continue
			elif line.startswith("\""):
				if current_coroutine:
					coroutines.append(current_coroutine)
				current_coroutine = Coroutine(line.split("state: ")[1], [])
			elif current_coroutine:
				current_coroutine.stack_trace.append(line)

		if current_coroutine:
			coroutines.append(current_coroutine)
	return coroutines

def build_tree(coroutines: list[Coroutine]) -> str:
	t = trie.StringTrie()
	for i, coroutine in enumerate(coroutines):
		frames = []
		for frame in reversed(coroutine.stack_trace):
			frames.append(f"{frame}\n")
			t.setdefault("".join(frames), []).append(i)

	stack: list[list[int]] = []
	indents: list[str] = []
	result: list[str] = []

	def traverse_callback(path_conv, path, children, labels=[]):
		nonlocal stack
		nonlocal indents
		nonlocal result

		# Forsing the subtree traverse
		children = list(children)
		if not labels:
			return

		label_in_top = False
		while stack and not label_in_top:
			for label in labels:
				if label in stack[-1]:
					label_in_top = True
					break
			if not label_in_top:
				if indents:
					indents.pop()
				stack.pop()


		if stack and labels != stack[-1]:
			indents.append("\t")

		indentation = "".join(indents)
		if not stack or labels != stack[-1]:
			result.append(f"{indentation}{len(labels)} Coroutine")
			if len(labels) > 1:
				result.append("s")

			for label in labels:
				result.append(f" {coroutines[label].state},")
			result.append("\n")

			stack.append(labels)

		last_frame = path[-1].splitlines()[-1]
		result.append(f"{indentation}\t{last_frame}\n")

	t.traverse(traverse_callback)

	return "".join(result)

def write_output(file_name: str, out: str):
	with open(file_name, "w") as f:
		f.write(out)

def main():
	for file_name in os.listdir(DUMP_DIR):
		coroutines = parse_dump(os.path.join(DUMP_DIR, file_name))
		out = build_tree(coroutines)
		write_output(os.path.join(OUT_DIR, file_name), out)

if __name__ == "__main__":
	main()
