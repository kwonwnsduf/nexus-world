package com.nexusworld.application.graph;
import com.nexusworld.domain.ontology.WorldGraphEntity;
import java.util.List;
public record EntityResolutionResult(
    String decision, WorldGraphEntity entity, List<String> matchedSchemes) {}
