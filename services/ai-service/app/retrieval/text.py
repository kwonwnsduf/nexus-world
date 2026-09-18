from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass

from app.retrieval.models import Chunk, Document

HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")


@dataclass(frozen=True)
class _Section:
    path: tuple[str, ...]
    text: str
    offset: int


class SectionAwareChunker:
    def __init__(self, target_characters: int = 1_200, overlap_characters: int = 160) -> None:
        if target_characters < 200:
            raise ValueError("target_characters must be at least 200")
        if overlap_characters < 0 or overlap_characters >= target_characters:
            raise ValueError("overlap_characters must be between 0 and target_characters")
        self.target_characters = target_characters
        self.overlap_characters = overlap_characters

    def chunk(self, document: Document) -> list[Chunk]:
        chunks: list[Chunk] = []
        ordinal = 0
        for section in self._sections(document.content):
            for text, relative_start in self._windows(section.text):
                normalized = text.strip()
                if not normalized:
                    continue
                left_trim = len(text) - len(text.lstrip())
                start = section.offset + relative_start + left_trim
                end = start + len(normalized)
                identity = f"{document.document_id}:{ordinal}:{start}:{end}"
                chunk_id = hashlib.sha256(identity.encode("utf-8")).hexdigest()[:32]
                chunks.append(
                    Chunk(
                        chunk_id=chunk_id,
                        document_id=document.document_id,
                        title=document.title,
                        source_uri=document.source_uri,
                        data_source_id=document.data_source_id,
                        evidence_id=document.evidence_id,
                        section_path=section.path,
                        content=normalized,
                        search_text="",
                        ordinal=ordinal,
                        start_character=start,
                        end_character=end,
                        embedding=(),
                        embedding_model="",
                    )
                )
                ordinal += 1
        return chunks

    def _sections(self, content: str) -> list[_Section]:
        lines = content.splitlines(keepends=True)
        headings: list[str] = []
        sections: list[_Section] = []
        buffer: list[str] = []
        buffer_offset = 0
        cursor = 0

        def flush() -> None:
            nonlocal buffer
            if buffer:
                sections.append(_Section(tuple(headings), "".join(buffer), buffer_offset))
                buffer = []

        for line in lines:
            match = HEADING_PATTERN.match(line.rstrip("\r\n"))
            if match:
                flush()
                level = len(match.group(1))
                headings[level - 1 :] = [match.group(2).strip()]
                buffer_offset = cursor + len(line)
            else:
                if not buffer:
                    buffer_offset = cursor
                buffer.append(line)
            cursor += len(line)
        flush()
        return sections or [_Section((), content, 0)]

    def _windows(self, value: str) -> list[tuple[str, int]]:
        if len(value) <= self.target_characters:
            return [(value, 0)]
        results: list[tuple[str, int]] = []
        cursor = 0
        while cursor < len(value):
            ideal_end = min(cursor + self.target_characters, len(value))
            end = ideal_end
            if ideal_end < len(value):
                boundary = max(
                    value.rfind("\n", cursor, ideal_end),
                    value.rfind(" ", cursor, ideal_end),
                )
                if boundary > cursor + self.target_characters // 2:
                    end = boundary
            results.append((value[cursor:end], cursor))
            if end >= len(value):
                break
            cursor = max(cursor + 1, end - self.overlap_characters)
        return results
