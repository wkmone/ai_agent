package com.wk.agent.entity.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Node("Concept")
public class ConceptNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("category")
    private String category;

    @Property("definition")
    private String definition;

    @Property("session_id")
    private String sessionId;

    @Property("importance")
    private Double importance;

    @Property("confidence")
    private Double confidence;

    @Property("created_at")
    private LocalDateTime createdAt;

    @Property("updated_at")
    private LocalDateTime updatedAt;

    @Property("access_count")
    private Integer accessCount;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private Set<ConceptRelation> relatedConcepts = new HashSet<>();

    public void addRelatedConcept(ConceptNode target, String relationType, Double weight) {
        ConceptRelation relation = new ConceptRelation();
        relation.setTarget(target);
        relation.setRelationType(relationType);
        relation.setWeight(weight);
        relation.setCreatedAt(LocalDateTime.now());
        relatedConcepts.add(relation);
    }
}
