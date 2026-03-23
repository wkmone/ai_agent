package com.wk.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wk.agent.entity.SessionMemory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionMemoryMapper extends BaseMapper<SessionMemory> {

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<SessionMemory> findBySessionId(String sessionId);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND memory_type = #{memoryType} ORDER BY created_at DESC")
    List<SessionMemory> findBySessionIdAndType(@Param("sessionId") String sessionId, @Param("memoryType") String memoryType);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND layer_level = #{layerLevel} ORDER BY created_at DESC LIMIT #{limit}")
    List<SessionMemory> findBySessionIdAndLayer(@Param("sessionId") String sessionId, @Param("layerLevel") int layerLevel, @Param("limit") int limit);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND layer_level = #{layerLevel} AND importance >= #{minImportance} ORDER BY importance DESC, created_at DESC LIMIT #{limit}")
    List<SessionMemory> findBySessionIdAndLayerWithMinImportance(
            @Param("sessionId") String sessionId,
            @Param("layerLevel") int layerLevel,
            @Param("minImportance") Double minImportance,
            @Param("limit") int limit);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND content LIKE CONCAT('%', #{keyword}, '%') ORDER BY created_at DESC")
    List<SessionMemory> findByKeyword(@Param("sessionId") String sessionId, @Param("keyword") String keyword);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND importance >= #{minImportance} ORDER BY importance DESC, created_at DESC LIMIT #{limit}")
    List<SessionMemory> findImportantMemories(@Param("sessionId") String sessionId, @Param("minImportance") Double minImportance, @Param("limit") int limit);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND layer_level = #{layerLevel} AND importance >= #{minImportance} ORDER BY importance DESC LIMIT #{limit}")
    List<SessionMemory> findImportantMemoriesByLayer(
            @Param("sessionId") String sessionId,
            @Param("layerLevel") int layerLevel,
            @Param("minImportance") Double minImportance,
            @Param("limit") int limit);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT #{limit}")
    List<SessionMemory> findRecentBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Update("UPDATE session_memory SET accessed_at = NOW(), access_count = access_count + 1 WHERE id = #{id}")
    void incrementAccessCount(@Param("id") Long id);

    @Delete("DELETE FROM session_memory WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM session_memory WHERE session_id = #{sessionId} AND layer_level = #{layerLevel}")
    int deleteBySessionIdAndLayer(@Param("sessionId") String sessionId, @Param("layerLevel") int layerLevel);

    @Select("SELECT COUNT(*) FROM session_memory WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT COUNT(*) FROM session_memory WHERE session_id = #{sessionId} AND layer_level = #{layerLevel}")
    int countBySessionIdAndLayer(@Param("sessionId") String sessionId, @Param("layerLevel") int layerLevel);

    @Select("SELECT * FROM session_memory WHERE expires_at IS NOT NULL AND expires_at < #{now}")
    List<SessionMemory> findExpiredMemories(@Param("now") LocalDateTime now);

    @Delete("DELETE FROM session_memory WHERE expires_at IS NOT NULL AND expires_at < #{now}")
    int deleteExpiredMemories(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND keywords LIKE CONCAT('%', #{keyword}, '%') ORDER BY importance DESC, created_at DESC")
    List<SessionMemory> findByKeywordInSession(@Param("sessionId") String sessionId, @Param("keyword") String keyword);
}