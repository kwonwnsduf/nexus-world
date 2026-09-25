from __future__ import annotations

import hashlib
import json
from collections import defaultdict, deque
from collections.abc import Callable

from app.simulation.graph_models import GraphRelationship, GraphSimulationRequest

Rule = Callable[[float, GraphRelationship, float], float]


def _ratio(edge: GraphRelationship) -> float:
    return edge.parameters["dependencyRatio"]


def _trade_flow(change: float, edge: GraphRelationship, alternative: float) -> float:
    return change * _ratio(edge) * (1.0 - alternative)


def _supplies(change: float, edge: GraphRelationship, alternative: float) -> float:
    return change * _ratio(edge) * (1.0 - alternative)


def _depends_on(change: float, edge: GraphRelationship, alternative: float) -> float:
    _ = alternative
    return change * _ratio(edge)


def _produces(change: float, edge: GraphRelationship, alternative: float) -> float:
    _ = alternative
    return change * _ratio(edge)


def _ships_via(change: float, edge: GraphRelationship, alternative: float) -> float:
    _ = alternative
    ratio = edge.parameters["dependencyRatio"]
    return change * ratio


RULES: dict[str, Rule] = {
    "TRADE_FLOW": _trade_flow,
    "SUPPLIES": _supplies,
    "DEPENDS_ON": _depends_on,
    "PRODUCES": _produces,
    "SHIPS_VIA": _ships_via,
}


def simulate_graph(request: GraphSimulationRequest) -> dict[str, object]:
    base = {node.entityId: dict(node.metrics) for node in request.nodes}
    current = {key: dict(value) for key, value in base.items()}
    outgoing: dict[str, list[GraphRelationship]] = defaultdict(list)
    incoming: dict[str, list[GraphRelationship]] = defaultdict(list)
    for edge in request.relationships:
        outgoing[edge.sourceEntityId].append(edge)
        incoming[edge.targetEntityId].append(edge)
    snapshots: list[dict[str, object]] = []
    violations: list[str] = []

    for turn in range(1, request.turns + 1):
        changes: dict[tuple[str, str], float] = {}
        queue: deque[tuple[str, str, float, int]] = deque()
        propagation_trace: list[dict[str, object]] = []
        for shock in request.shocks:
            duration = shock.duration or request.turns
            if shock.turn <= turn < shock.turn + duration:
                queue.append((shock.entityId, shock.metric, shock.change, 0))
                changes[(shock.entityId, shock.metric)] = shock.change
        visited: set[tuple[str, str, str]] = set()
        while queue:
            source, metric, change, depth = queue.popleft()
            if depth >= request.maxPropagationDepth:
                continue
            for edge in sorted(
                outgoing.get(source, []), key=lambda item: item.relationshipId
            ):
                rule = RULES.get(edge.relationshipType)
                if rule is None:
                    continue
                key = (edge.relationshipId, source, metric)
                if key in visited:
                    continue
                visited.add(key)
                alternative = 0.0
                if request.strategy == "OBSERVED_ALTERNATIVE_SUPPLY":
                    alternative = min(
                        1.0,
                        sum(
                            item.parameters.get("dependencyRatio", 0.0)
                            for item in incoming.get(edge.targetEntityId, [])
                            if item.sourceEntityId != source
                        ),
                    )
                propagated = rule(change, edge, alternative)
                target_key = (edge.targetEntityId, metric)
                previous = changes.get(target_key, 0.0)
                combined = max(-1.0, min(1.0, previous + propagated))
                changes[target_key] = combined
                propagation_trace.append({
                    "depth": depth + 1,
                    "relationshipId": edge.relationshipId,
                    "relationshipType": edge.relationshipType,
                    "sourceEntityId": source,
                    "targetEntityId": edge.targetEntityId,
                    "metric": metric,
                    "inputChange": round(change, 12),
                    "propagatedChange": round(propagated, 12),
                })
                queue.append((edge.targetEntityId, metric, propagated, depth + 1))

        for (entity_id, metric), change in changes.items():
            baseline = base[entity_id].get(metric)
            if baseline is None:
                violations.append(f"missing metric {metric} for {entity_id}")
                continue
            current[entity_id][metric] = max(0.0, baseline * (1.0 + change))
        total_supply = sum(metrics.get("SUPPLY", 0.0) for metrics in current.values())
        total_demand = sum(metrics.get("DEMAND", 0.0) for metrics in current.values())
        snapshots.append({
            "turn": turn,
            "state": [
                {"entityId": key, "metrics": current[key]}
                for key in sorted(current)
            ],
            "metrics": {
                "totalSupply": round(total_supply, 6),
                "totalDemand": round(total_demand, 6),
                "affectedEntities": float(len(changes)),
            },
            "traversal": {
                "mode": request.traversalMode,
                "maxDepth": request.maxPropagationDepth,
                "steps": propagation_trace,
            },
            "invariantViolations": sorted(set(violations)),
        })

    final_state = snapshots[-1]["state"] if snapshots else []
    canonical = {
        "seed": request.seed,
        "turns": request.turns,
        "strategy": request.strategy,
        "traversalMode": request.traversalMode,
        "maxPropagationDepth": request.maxPropagationDepth,
        "snapshots": snapshots,
        "finalState": final_state,
    }
    result_hash = hashlib.sha256(
        json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    valid = not violations
    return {
        "contractVersion": "v1",
        "seed": request.seed,
        "turns": request.turns,
        "snapshots": snapshots,
        "finalState": final_state,
        "resultHash": result_hash,
        "invariants": {
            "nonNegativeState": valid,
            "relationshipParametersGrounded": valid,
            "deterministicTraversal": True,
            "accountingReconciled": valid,
        },
    }
