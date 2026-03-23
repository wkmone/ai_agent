package com.wk.agent.repository.neo4j;

import com.wk.agent.entity.neo4j.ConceptNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConceptNodeRepository extends Neo4jRepository<ConceptNode, Long> {

    Optional<ConceptNode> findByName(String name);

    List<ConceptNode> findByNameContaining(String keyword);

    List<ConceptNode> findByCategory(String category);

    List<ConceptNode> findBySessionId(String sessionId);

    List<ConceptNode> findByImportanceGreaterThan(Double importance);

    List<ConceptNode> findByImportanceLessThan(Double importance);

    long deleteByImportanceLessThan(Double importance);

    @Query("MATCH (c:Concept)-[r:RELATED_TO]->(related:Concept) WHERE c.name = $name RETURN related")
    List<ConceptNode> findRelatedConcepts(@Param("name") String name);

    @Query("MATCH path = (c:Concept)-[:RELATED_TO*1..3]->(related:Concept) WHERE c.name = $name RETURN related")
    List<ConceptNode> findRelatedConceptsInDepth(@Param("name") String name);

    @Query("MATCH (c:Concept {session_id: $sessionId}) DETACH DELETE c")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Query("MATCH (c:Concept) RETURN count(c)")
    long countAll();

    @Query("MATCH (c:Concept) WHERE c.category = $category RETURN avg(c.importance)")
    Double getAverageImportanceByCategory(@Param("category") String category);
}
