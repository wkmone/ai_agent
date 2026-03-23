package com.wk.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wk.agent.entity.AgentNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface AgentNoteMapper extends BaseMapper<AgentNote> {

    @Select("SELECT * FROM agent_note WHERE session_id = #{sessionId} ORDER BY importance DESC, created_at DESC")
    List<AgentNote> findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM agent_note WHERE session_id = #{sessionId} AND note_type = #{noteType} ORDER BY created_at DESC")
    List<AgentNote> findBySessionIdAndType(@Param("sessionId") String sessionId, @Param("noteType") String noteType);

    @Select("SELECT * FROM agent_note WHERE note_id = #{noteId}")
    AgentNote findByNoteId(@Param("noteId") String noteId);

    @Select("SELECT * FROM agent_note WHERE session_id = #{sessionId} AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%'))")
    List<AgentNote> searchByKeyword(@Param("sessionId") String sessionId, @Param("keyword") String keyword);

    @Select("SELECT * FROM agent_note WHERE project_name = #{projectName} ORDER BY importance DESC, updated_at DESC")
    List<AgentNote> findByProjectName(@Param("projectName") String projectName);

    @Select("SELECT COUNT(*) FROM agent_note WHERE session_id = #{sessionId}")
    Integer countBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT DISTINCT note_type FROM agent_note WHERE session_id = #{sessionId}")
    List<String> findDistinctTypesBySessionId(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM agent_note WHERE note_id = #{noteId}")
    void deleteByNoteId(@Param("noteId") String noteId);

    @Delete("DELETE FROM agent_note WHERE session_id = #{sessionId}")
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
