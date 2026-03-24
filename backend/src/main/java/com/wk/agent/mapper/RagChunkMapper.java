package com.wk.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wk.agent.entity.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    @Select("SELECT * FROM rag_chunk WHERE rag_namespace = #{ragNamespace}")
    List<RagChunk> findByRagNamespace(@Param("ragNamespace") String ragNamespace);

    @Select("SELECT * FROM rag_chunk WHERE knowledge_base_id = #{knowledgeBaseId}")
    List<RagChunk> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("SELECT * FROM rag_chunk WHERE document_id = #{documentId}")
    List<RagChunk> findByDocumentId(@Param("documentId") String documentId);

    @Select("SELECT DISTINCT document_id FROM rag_chunk WHERE rag_namespace = #{ragNamespace}")
    List<String> findDistinctDocumentIdsByNamespace(@Param("ragNamespace") String ragNamespace);

    @Select("SELECT DISTINCT document_id FROM rag_chunk WHERE knowledge_base_id = #{knowledgeBaseId}")
    List<String> findDistinctDocumentIdsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("SELECT COUNT(*) FROM rag_chunk WHERE rag_namespace = #{ragNamespace}")
    Integer countByRagNamespace(@Param("ragNamespace") String ragNamespace);

    @Select("SELECT COUNT(*) FROM rag_chunk WHERE knowledge_base_id = #{knowledgeBaseId}")
    Integer countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("SELECT COUNT(*) FROM rag_chunk WHERE document_id = #{documentId}")
    Integer countByDocumentId(@Param("documentId") String documentId);

    @Delete("DELETE FROM rag_chunk WHERE document_id = #{documentId}")
    void deleteByDocumentId(@Param("documentId") String documentId);

    @Delete("DELETE FROM rag_chunk WHERE knowledge_base_id = #{knowledgeBaseId}")
    void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    @Select("SELECT * FROM rag_chunk WHERE document_id = #{documentId} AND chunk_index >= #{startIndex} AND chunk_index <= #{endIndex} ORDER BY chunk_index")
    List<RagChunk> findByDocumentIdAndIndexRange(@Param("documentId") String documentId, 
                                                    @Param("startIndex") int startIndex, 
                                                    @Param("endIndex") int endIndex);
}
