package com.wk.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 终端工具
 * 提供文件系统操作和命令执行功能
 * 
 * 使用Spring AI的@Tool注解，自动被Spring AI发现和注册
 */
@Component
public class TerminalTool {
    
    private static final Logger log = LoggerFactory.getLogger(TerminalTool.class);
    
    @Value("${agent.terminal.enabled:false}")
    private boolean terminalEnabled;
    
    @Value("${agent.terminal.work-dir:./workspace}")
    private String workDir;
    
    @Value("${agent.terminal.timeout:30000}")
    private long timeoutMs;
    
    @Value("${agent.terminal.max-output:10000}")
    private int maxOutputLength;
    
    private final Map<String, Path> sessionCurrentDirs = new ConcurrentHashMap<>();
    
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "rm -rf /", "rm -rf /*", "mkfs", "dd if=/dev/zero",
        ":(){ :|:& };:", "chmod -R 777 /", "chown -R",
        "> /dev/sda", "wget", "curl -X POST", "curl -X PUT",
        "curl -X DELETE", "nc -l", "ncat", "ssh", "scp",
        "rsync", "passwd", "su -", "sudo su", "visudo",
        "crontab -e", "systemctl", "service", "apt-get",
        "yum", "brew install", "npm install -g", "pip install",
        "conda install"
    );
    
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "ls", "dir", "cat", "head", "tail", "grep", "find",
        "wc", "sort", "uniq", "cut", "awk", "sed", "echo",
        "pwd", "whoami", "date", "tree", "file", "stat",
        "du", "df", "free", "ps", "top", "uname", "hostname",
        "env", "printenv", "which", "whereis", "type", "history",
        "git status", "git log", "git diff", "git branch",
        "git remote", "git show"
    );
    
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        "(;|\\|\\||&&|\\|)\\s*(rm|chmod|chown|mkfs|dd|wget|curl|nc|ssh|scp|sudo|su|apt|yum|brew|npm|pip)",
        Pattern.CASE_INSENSITIVE
    );
    
    @Tool(description = "执行终端命令。支持安全的命令执行，自动阻止危险命令。")
    public String runCommand(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "会话ID，用于保持目录状态") String sessionId) {
        
        if (!terminalEnabled) {
            return "❌ Terminal工具未启用。请在配置中设置 agent.terminal.enabled=true";
        }
        
        if (command == null || command.isEmpty()) {
            return "❌ 命令不能为空";
        }
        
        String securityCheck = validateCommand(command);
        if (securityCheck != null) {
            return securityCheck;
        }
        
        Path currentDir = getCurrentDir(sessionId);
        
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            pb.directory(currentDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > maxOutputLength) {
                        output.append("\n[输出被截断，超过最大长度限制]");
                        break;
                    }
                }
            }
            
            boolean finished = process.waitFor(timeoutMs / 1000, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "❌ 命令执行超时 (超过 " + (timeoutMs / 1000) + " 秒)";
            }
            
            int exitCode = process.exitValue();
            String result = output.toString().trim();
            
            if (result.isEmpty()) {
                result = "(命令执行完成，无输出)";
            }
            
            return String.format("✅ 命令执行成功 (退出码: %d)\n📁 目录: %s\n%s", 
                exitCode, currentDir, result);
            
        } catch (IOException e) {
            return "❌ 命令执行失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ 命令执行被中断";
        }
    }
    
    @Tool(description = "读取文件内容。支持行号显示和行数限制。")
    public String readFile(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "最大显示行数，默认100") Integer maxLines) {
        
        if (path == null || path.isEmpty()) {
            return "❌ 文件路径不能为空";
        }
        
        if (maxLines == null) {
            maxLines = 100;
        }
        
        Path filePath = resolvePath(path);
        if (!isPathAllowed(filePath)) {
            return "❌ 不允许访问此路径: " + path;
        }
        
        if (!Files.exists(filePath)) {
            return "❌ 文件不存在: " + path;
        }
        
        if (!Files.isRegularFile(filePath)) {
            return "❌ 不是常规文件: " + path;
        }
        
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            sb.append("📄 文件: ").append(filePath).append("\n");
            sb.append("═══════════════════════════════════════\n");
            
            int count = 0;
            for (String line : lines) {
                if (count >= maxLines) {
                    sb.append("\n... 省略剩余 ").append(lines.size() - maxLines).append(" 行");
                    break;
                }
                sb.append(String.format("%4d→%s\n", count + 1, line));
                count++;
            }
            
            sb.append("\n总计: ").append(lines.size()).append(" 行");
            return sb.toString();
            
        } catch (IOException e) {
            return "❌ 读取文件失败: " + e.getMessage();
        }
    }
    
    @Tool(description = "列出目录内容。支持显示隐藏文件和排序。")
    public String listDirectory(
            @ToolParam(description = "目录路径，默认为工作目录") String path,
            @ToolParam(description = "是否显示隐藏文件，默认false") Boolean showHidden) {
        
        if (path == null || path.isEmpty()) {
            path = workDir;
        }
        
        final boolean showHiddenFiles = showHidden != null ? showHidden : false;
        
        Path dirPath = resolvePath(path);
        if (!isPathAllowed(dirPath)) {
            return "❌ 不允许访问此路径: " + path;
        }
        
        if (!Files.exists(dirPath)) {
            return "❌ 目录不存在: " + path;
        }
        
        if (!Files.isDirectory(dirPath)) {
            return "❌ 不是目录: " + path;
        }
        
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("📁 目录: ").append(dirPath.toAbsolutePath()).append("\n");
            sb.append("═══════════════════════════════════════\n");
            
            Files.list(dirPath)
                .filter(p -> showHiddenFiles || !p.getFileName().toString().startsWith("."))
                .sorted((a, b) -> {
                    boolean aIsDir = Files.isDirectory(a);
                    boolean bIsDir = Files.isDirectory(b);
                    if (aIsDir && !bIsDir) return -1;
                    if (!aIsDir && bIsDir) return 1;
                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                })
                .forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        String type = Files.isDirectory(p) ? "📁" : "📄";
                        String size = Files.isDirectory(p) ? "" : 
                            String.format(" (%s)", formatSize(Files.size(p)));
                        sb.append(String.format("%s %s%s\n", type, name, size));
                    } catch (IOException e) {
                        sb.append("❓ ").append(p.getFileName()).append("\n");
                    }
                });
            
            return sb.toString();
            
        } catch (IOException e) {
            return "❌ 列出目录失败: " + e.getMessage();
        }
    }
    
    @Tool(description = "显示目录树结构。支持深度限制。")
    public String showTree(
            @ToolParam(description = "目录路径，默认为工作目录") String path,
            @ToolParam(description = "最大深度，默认3") Integer maxDepth) {
        
        if (path == null || path.isEmpty()) {
            path = workDir;
        }
        
        if (maxDepth == null) {
            maxDepth = 3;
        }
        
        Path dirPath = resolvePath(path);
        if (!isPathAllowed(dirPath)) {
            return "❌ 不允许访问此路径: " + path;
        }
        
        if (!Files.exists(dirPath)) {
            return "❌ 目录不存在: " + path;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("🌳 目录树: ").append(dirPath.getFileName()).append("\n");
        sb.append("═══════════════════════════════════════\n");
        
        buildTree(sb, dirPath, "", maxDepth, 0);
        
        return sb.toString();
    }
    
    private Path getCurrentDir(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Paths.get(workDir).normalize().toAbsolutePath();
        }
        return sessionCurrentDirs.computeIfAbsent(sessionId, 
            k -> Paths.get(workDir).normalize().toAbsolutePath());
    }
    
    private Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            Path baseDir = Paths.get(workDir).normalize().toAbsolutePath();
            path = baseDir.resolve(path);
        }
        return path.normalize();
    }
    
    private boolean isPathAllowed(Path path) {
        try {
            Path normalizedPath = path.normalize().toAbsolutePath();
            Path workPath = Paths.get(workDir).normalize().toAbsolutePath();
            return normalizedPath.startsWith(workPath);
        } catch (Exception e) {
            return false;
        }
    }
    
    private String validateCommand(String command) {
        String commandLower = command.toLowerCase().trim();
        
        for (String blocked : BLOCKED_COMMANDS) {
            if (commandLower.contains(blocked.toLowerCase())) {
                return "❌ 命令被阻止: 包含不允许的操作 '" + blocked + "'";
            }
        }
        
        if (DANGEROUS_PATTERN.matcher(command).find()) {
            return "❌ 命令被阻止: 包含潜在危险的命令组合";
        }
        
        String baseCommand = commandLower.split("\\s+")[0];
        boolean isAllowed = ALLOWED_COMMANDS.stream()
            .anyMatch(allowed -> baseCommand.equals(allowed.split("\\s+")[0]) || 
                commandLower.startsWith(allowed.toLowerCase()));
        
        if (!isAllowed) {
            log.warn("尝试执行未允许的命令: {}", command);
            return "⚠️ 命令不在允许列表中。允许的命令: ls, cat, head, tail, grep, find, tree, git status 等";
        }
        
        return null;
    }
    
    private void buildTree(StringBuilder sb, Path path, String prefix, int maxDepth, int depth) {
        if (depth > maxDepth) {
            return;
        }
        
        try {
            List<Path> children = Files.list(path)
                .sorted((a, b) -> {
                    boolean aIsDir = Files.isDirectory(a);
                    boolean bIsDir = Files.isDirectory(b);
                    if (aIsDir && !bIsDir) return -1;
                    if (!aIsDir && bIsDir) return 1;
                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                })
                .limit(50)
                .toList();
            
            for (int i = 0; i < children.size(); i++) {
                Path child = children.get(i);
                boolean isLast = (i == children.size() - 1);
                String connector = isLast ? "└── " : "├── ";
                String type = Files.isDirectory(child) ? "📁" : "📄";
                
                sb.append(prefix).append(connector).append(type).append(" ")
                    .append(child.getFileName()).append("\n");
                
                if (Files.isDirectory(child) && depth < maxDepth) {
                    String newPrefix = prefix + (isLast ? "    " : "│   ");
                    buildTree(sb, child, newPrefix, maxDepth, depth + 1);
                }
            }
        } catch (IOException e) {
            sb.append(prefix).append("❌ 无法读取目录\n");
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
