package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent任务实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_task")
public class AgentTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 任务类型
     */
    private String type;

    /**
     * 任务内容
     */
    private String content;

    /**
     * 任务参数
     */
    private String params;

    /**
     * 任务状态：0-待处理，1-处理中，2-已完成，3-失败
     */
    private Integer status;

    /**
     * 任务结果
     */
    private String result;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    private Integer deleted;
}
