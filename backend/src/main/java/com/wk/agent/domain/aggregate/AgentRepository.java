package com.wk.agent.domain.aggregate;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {
    AgentAggregate save(AgentAggregate agent);
    Optional<AgentAggregate> findById(String id);
    Optional<AgentAggregate> findByName(String name);
    List<AgentAggregate> findAll();
    void deleteById(String id);
}