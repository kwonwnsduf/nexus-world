from __future__ import annotations

import re
import unicodedata
from typing import Protocol

from kiwipiepy import Kiwi  # type: ignore[import-untyped]

TOKEN_PATTERN = re.compile(r"[^\W_]+", re.UNICODE)


class SearchTextNormalizer(Protocol):
    version: str

    def normalize(self, value: str) -> str: ...


def normalize_unicode(value: str) -> str:
    return unicodedata.normalize("NFKC", value).casefold()


def lexical_tokens(value: str) -> list[str]:
    return TOKEN_PATTERN.findall(normalize_unicode(value))


class KiwiSearchTextNormalizer:
    version = "kiwi-v1"
    _USEFUL_POS = frozenset(
        {
            "NNG",
            "NNP",
            "NNB",
            "NR",
            "NP",
            "VV",
            "VA",
            "VX",
            "VCP",
            "VCN",
            "MAG",
            "XR",
            "SL",
            "SH",
            "SN",
        }
    )

    def __init__(self, kiwi: Kiwi | None = None) -> None:
        self._kiwi = kiwi

    def normalize(self, value: str) -> str:
        normalized = normalize_unicode(value)
        if self._kiwi is None:
            self._kiwi = Kiwi(num_workers=1)
        forms = [
            token.form.casefold()
            for token in self._kiwi.tokenize(normalized)
            if token.tag in self._USEFUL_POS and token.form.strip()
        ]
        return " ".join(forms)


class SimpleSearchTextNormalizer:
    """Legacy evaluator only; never used as a production fallback."""

    version = "simple-v1"

    def normalize(self, value: str) -> str:
        return " ".join(lexical_tokens(value))
