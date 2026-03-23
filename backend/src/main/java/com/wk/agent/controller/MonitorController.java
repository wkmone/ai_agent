package com.wk.agent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@Tag(name = "Health", description = "健康检查和监控接口")
public class MonitorController {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查系统各组件的健康状态")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> components = new HashMap<>();
        
        boolean allHealthy = true;
        
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        components.put("memory", checkMemory());
        components.put("system", checkSystem());
        
        for (Object status : components.values()) {
            if (status instanceof Map) {
                Map<?, ?> statusMap = (Map<?, ?>) status;
                if ("DOWN".equals(statusMap.get("status"))) {
                    allHealthy = false;
                    break;
                }
            }
        }
        
        health.put("status", allHealthy ? "UP" : "DOWN");
        health.put("components", components);
        health.put("timestamp", System.currentTimeMillis());
        
        if (buildProperties != null) {
            health.put("version", buildProperties.getVersion());
            health.put("application", buildProperties.getName());
        }
        
        return ResponseEntity.status(allHealthy ? 200 : 503).body(health);
    }

    @GetMapping("/metrics")
    @Operation(summary = "系统指标", description = "获取系统运行指标")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsed", memoryMXBean.getHeapMemoryUsage().getUsed());
        memory.put("heapMax", memoryMXBean.getHeapMemoryUsage().getMax());
        memory.put("heapCommitted", memoryMXBean.getHeapMemoryUsage().getCommitted());
        memory.put("nonHeapUsed", memoryMXBean.getNonHeapMemoryUsage().getUsed());
        metrics.put("memory", memory);
        
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> system = new HashMap<>();
        system.put("availableProcessors", osMXBean.getAvailableProcessors());
        system.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        system.put("arch", osMXBean.getArch());
        system.put("osName", osMXBean.getName());
        system.put("osVersion", osMXBean.getVersion());
        metrics.put("system", system);
        
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("totalMemory", runtime.totalMemory());
        jvm.put("freeMemory", runtime.freeMemory());
        jvm.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvm.put("maxMemory", runtime.maxMemory());
        jvm.put("availableProcessors", runtime.availableProcessors());
        metrics.put("jvm", jvm);
        
        Map<String, Object> threads = new HashMap<>();
        threads.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        threads.put("peakThreadCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        threads.put("daemonThreadCount", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        metrics.put("threads", threads);
        
        metrics.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        metrics.put("startTime", ManagementFactory.getRuntimeMXBean().getStartTime());
        
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/ping")
    @Operation(summary = "心跳检测", description = "简单的服务存活检测")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new HashMap<>();
        try {
            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection()) {
                    boolean valid = conn.isValid(5);
                    status.put("status", valid ? "UP" : "DOWN");
                    status.put("database", conn.getMetaData().getDatabaseProductName());
                    status.put("version", conn.getMetaData().getDatabaseProductVersion());
                }
            } else {
                status.put("status", "UNKNOWN");
                status.put("message", "DataSource not configured");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new HashMap<>();
        try {
            if (redisConnectionFactory != null) {
                var connection = redisConnectionFactory.getConnection();
                String pong = connection.ping();
                status.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
                connection.close();
            } else {
                status.put("status", "UNKNOWN");
                status.put("message", "Redis not configured");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkMemory() {
        Map<String, Object> status = new HashMap<>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        double usagePercent = (double) used / max * 100;
        
        status.put("status", usagePercent > 90 ? "DOWN" : (usagePercent > 80 ? "WARNING" : "UP"));
        status.put("heapUsagePercent", String.format("%.2f%%", usagePercent));
        status.put("usedBytes", used);
        status.put("maxBytes", max);
        return status;
    }

    private Map<String, Object> checkSystem() {
        Map<String, Object> status = new HashMap<>();
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        double loadAverage = osMXBean.getSystemLoadAverage();
        int processors = osMXBean.getAvailableProcessors();
        double loadPercent = (loadAverage / processors) * 100;
        
        status.put("status", loadPercent > 100 ? "DOWN" : (loadPercent > 80 ? "WARNING" : "UP"));
        status.put("systemLoadAverage", loadAverage);
        status.put("loadPercent", String.format("%.2f%%", loadPercent));
        status.put("availableProcessors", processors);
        return status;
    }
}
