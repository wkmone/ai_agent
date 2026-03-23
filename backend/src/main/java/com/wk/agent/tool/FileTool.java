package com.wk.agent.tool;

import cn.hutool.core.io.FileUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 文件服务工具
 */
@Component
public class FileTool {
    
    @Tool(description = "读取文件内容")
    public String readFile(
            @ToolParam(description = "文件路径") String path) {
        try {
            if (!FileUtil.exist(path)) {
                return "文件不存在";
            }
            return FileUtil.readUtf8String(path);
        } catch (Exception e) {
            return "读取文件时出错: " + e.getMessage();
        }
    }
    
    @Tool(description = "写入文件内容")
    public String writeFile(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "文件内容") String content) {
        try {
            FileUtil.writeUtf8String(content, path);
            return "文件写入成功";
        } catch (Exception e) {
            return "写入文件时出错: " + e.getMessage();
        }
    }
    
    @Tool(description = "列出目录下的文件")
    public String listFiles(
            @ToolParam(description = "目录路径") String path) {
        try {
            if (!FileUtil.exist(path)) {
                return "路径不存在";
            }
            if (!FileUtil.isDirectory(path)) {
                return "路径不是目录";
            }
            StringBuilder result = new StringBuilder();
            java.io.File[] files = FileUtil.ls(path);
            for (java.io.File file : files) {
                result.append(file.getName());
                if (file.isDirectory()) {
                    result.append("/");
                }
                result.append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            return "列出文件时出错: " + e.getMessage();
        }
    }
    
    @Tool(description = "创建目录")
    public String createDir(
            @ToolParam(description = "目录路径") String path) {
        try {
            FileUtil.mkdir(path);
            return "目录创建成功";
        } catch (Exception e) {
            return "创建目录时出错: " + e.getMessage();
        }
    }
    
    @Tool(description = "删除文件或目录")
    public String deleteFile(
            @ToolParam(description = "文件或目录路径") String path) {
        try {
            if (!FileUtil.exist(path)) {
                return "路径不存在";
            }
            FileUtil.del(path);
            return "删除成功";
        } catch (Exception e) {
            return "删除时出错: " + e.getMessage();
        }
    }
}
