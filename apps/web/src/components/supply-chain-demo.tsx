"use client";

import { useState } from "react";
import type { SupplyChainDemoResult } from "@/lib/supply-chain-demo";

function format(value: number) {
  return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(value);
}

export function SupplyChainDemo() {
  const [query, setQuery] = useState("중국 반도체 공급이 50% 감소하면?");
  const [result, setResult] = useState<SupplyChainDemoResult>();
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");

  async function run() {
    setRunning(true);
    setError("");
    try {
      const response = await fetch("/api/demo/supply-chain", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query }),
      });
      const body = await response.json().catch(() => ({})) as SupplyChainDemoResult & { detail?: string };
      if (!response.ok) throw new Error(body.detail ?? "근거 기반 시나리오를 실행할 수 없습니다.");
      setResult(body);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "근거 기반 시나리오를 실행할 수 없습니다.");
    } finally {
      setRunning(false);
    }
  }

  return (
    <section className="demo" aria-labelledby="demo-title">
      <div className="demo-heading">
        <div>
          <p className="eyebrow">PHASE 1 · GROUNDED PATH</p>
          <h2 id="demo-title">실제 데이터 기반 공급망 시나리오</h2>
          <p>자연어 충격을 하나의 변경 불가 World Version에서 해석·탐색·시뮬레이션합니다.</p>
        </div>
        <div className="scenario-input">
          <label htmlFor="scenario-query">What-if 충격을 자연어로 입력하세요</label>
          <textarea id="scenario-query" value={query} maxLength={500}
            onChange={(event) => setQuery(event.target.value)} />
          <button type="button" onClick={run} disabled={running || query.trim().length < 3}>
            {running ? "근거와 그래프 확인 중…" : "그래프 탐색 및 실행"}
          </button>
        </div>
      </div>
      {error && <p className="status down">{error}</p>}
      {result && (
        <>
          <article className="mapping-card">
            <div><span>상태</span><strong>{result.status}</strong></div>
            <div><span>World Version</span><strong>{result.worldVersionId}</strong></div>
            <div><span>해결된 엔티티</span><strong>{result.target.displayName}</strong></div>
            <div><span>온톨로지 타입</span><strong>{result.target.entityType}</strong></div>
            <div><span>충격</span><strong>{result.shock.metric} {format(result.shock.change * 100)}%</strong></div>
            <div><span>수치 근거</span><strong>{result.shock.basisType}</strong></div>
            <div><span>그래프 근거</span><strong>{result.graph.evidenceCount}</strong></div>
            <div><span>텍스트 인용</span><strong>{result.graph.citationCount}</strong></div>
            <div><span>데이터 기준일</span><strong>{result.worldManifest.asOfDate}</strong></div>
            <div><span>World 생성 시각</span><strong>{result.worldManifest.createdAt}</strong></div>
            <div><span>RAG 인덱스</span><strong>{result.worldManifest.retrievalIndexStatus} · {result.worldManifest.retrievalDocumentCount}/{result.worldManifest.expectedRetrievalDocumentCount}</strong></div>
            <div><span>온톨로지 버전</span><strong>{result.worldManifest.ontologyVersion}</strong></div>
            <div><span>시뮬레이션 규칙</span><strong>{result.worldManifest.simulationRuleVersion}</strong></div>
            <div><span>소스 스냅샷</span><strong>{result.worldManifest.sourceSnapshotCount}</strong></div>
            <div><span>기준값 근거</span><strong>{result.provenance.baselineValueCount}</strong></div>
            <div><span>관계 파라미터 근거</span><strong>{result.provenance.relationshipParameterCount}</strong></div>
            <div><span>충격 분류</span><strong>{result.provenance.shock}</strong></div>
            <div className="mapping-wide"><span>GraphRAG</span><strong>{result.graph.answer}</strong></div>
          </article>

          {result.graph.paths.map((path, index) => (
            <p className="run-id" key={`${path.score}-${index}`}>
              {path.nodes.map((node, nodeIndex) => (
                <span key={`${node.displayName}-${nodeIndex}`}>
                  {nodeIndex > 0 && ` → ${path.relationships[nodeIndex - 1]?.relationshipType ?? "?"} → `}
                  {node.displayName}
                </span>
              ))}
            </p>
          ))}

          {(result.graph.evidence.length > 0 || result.graph.citations.length > 0) && (
            <article className="mapping-card">
              <div className="mapping-wide"><span>그래프 근거</span><strong>
                {result.graph.evidence.map((item, index) => item.sourceUri ? (
                  <a key={`${item.evidenceId}-${index}`} href={item.sourceUri}
                    target="_blank" rel="noreferrer">{item.ownerKind} #{index + 1}</a>
                ) : <em key={`${item.evidenceId}-${index}`}>{item.ownerKind} #{index + 1}</em>)}
              </strong></div>
              <div className="mapping-wide"><span>검색 인용</span><strong>
                {result.graph.citations.map((item, index) => item.sourceUri ? (
                  <a key={`${item.title}-${index}`} href={item.sourceUri}
                    target="_blank" rel="noreferrer">{item.title}</a>
                ) : <em key={`${item.title}-${index}`}>{item.title}</em>)}
              </strong></div>
            </article>
          )}

          {!result.dataQuality.ready && (
            <article className="status down">
              <strong>{result.dataQuality.message}</strong>
              <p>누락: {result.dataQuality.missing.join(", ")}</p>
              <p>없는 수치는 만들지 않았습니다. 확인된 근거와 그래프 경로만 표시합니다.</p>
            </article>
          )}

          {result.branches.length > 0 && (
            <div className="branch-grid">
              {result.branches.map((branch) => (
                <article className="branch" key={branch.name}>
                  <div className="branch-title">
                    <h3>{branch.name}</h3>
                    <span className={branch.invariantsPassed ? "pill ok" : "pill bad"}>
                      {branch.invariantsPassed ? "불변식 통과" : "불변식 실패"}
                    </span>
                  </div>
                  <dl>{Object.entries(branch.metrics).map(([name, value]) => (
                    <div key={name}><dt>{name}</dt><dd>{format(value)}</dd></div>
                  ))}</dl>
                  <small>seed {branch.seed} · replay {branch.resultHash.slice(0, 12)}</small>
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
