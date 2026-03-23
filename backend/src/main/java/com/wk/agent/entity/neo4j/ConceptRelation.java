package com.wk.agent.entity.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;

@Data
@RelationshipProperties
public class ConceptRelation {

    @Id
    @GeneratedValue
    private Long id;

    @TargetNode
    private ConceptNode target;

    private String relationType;

    private Double weight;

    private LocalDateTime createdAt;
}
