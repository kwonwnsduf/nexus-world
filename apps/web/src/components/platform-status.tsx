"use client";

import { useEffect, useState } from "react";
import type { PlatformStatus } from "@/lib/platform-status";

type LoadState =
  | { phase: "loading" }
  | { phase: "ready"; data: PlatformStatus }
  | { phase: "error" };

export function PlatformStatusCard() {
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  useEffect(() => {
    let active = true;
    fetch("/api/platform/status", { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error("Platform unavailable");
        return (await response.json()) as PlatformStatus;
      })
      .then((data) => active && setState({ phase: "ready", data }))
      .catch(() => active && setState({ phase: "error" }));
    return () => {
      active = false;
    };
  }, []);

  if (state.phase === "loading") return <p className="status">Checking service contract…</p>;
  if (state.phase === "error") return <p className="status down">Platform contract unavailable</p>;

  return (
    <section className="status-card">
      <p className="status up">Web → Core → AI contract connected</p>
      <p>
        {state.data.service} {state.data.contractVersion} · {state.data.downstream.service}{" "}
        {state.data.downstream.contractVersion}
      </p>
      <small>{state.data.downstream.capabilities.join(" · ")}</small>
    </section>
  );
}

