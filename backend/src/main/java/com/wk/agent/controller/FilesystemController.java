package com.wk.agent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/v1/filesystem")
public class FilesystemController {
    
    @Value("${app.codebase.allowed-base-paths:/Users,/home,/var,/opt}")
    private String allowedBasePaths;
    
    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browseDirectory(
            @RequestParam(required = false) String path,
            @RequestParam(required = false, defaultValue = "false") boolean dirsOnly) {
        
        try {
            if (path == null || path.isEmpty()) {
                return getRootDirectories();
            }
            
            Path currentPath = Paths.get(path);
            if (!Files.exists(currentPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径不存在: " + path));
            }
            
            if (!isPathAllowed(path)) {
                return ResponseEntity.status(403).body(Map.of("error", "无权访问此路径"));
            }
            
            File dir = currentPath.toFile();
            if (!dir.isDirectory()) {
                return ResponseEntity.badRequest().body(Map.of("error", "不是有效的目录"));
            }
            
            File[] files = dir.listFiles();
            List<Map<String, Object>> items = new ArrayList<>();
            
            if (files != null) {
                for (File file : files) {
                    if (dirsOnly && !file.isDirectory()) {
                        continue;
                    }
                    if (file.isHidden()) {
                        continue;
                    }
                    items.add(Map.of(
                        "name", file.getName(),
                        "path", file.getAbsolutePath(),
                        "isDirectory", file.isDirectory(),
                        "size", file.isDirectory() ? 0 : file.length(),
                        "lastModified", file.lastModified()
                    ));
                }
            }
            
            items.sort((a, b) -> {
                boolean aDir = (boolean) a.get("isDirectory");
                boolean bDir = (boolean) b.get("isDirectory");
                if (aDir != bDir) {
                    return aDir ? -1 : 1;
                }
                return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
            });
            
            String parentPath = currentPath.getParent() != null ? 
                    currentPath.getParent().toString() : null;
            
            return ResponseEntity.ok(Map.of(
                "currentPath", path,
                "parentPath", parentPath,
                "items", items
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validatePath(@RequestParam String path) {
        try {
            Path p = Paths.get(path);
            boolean exists = Files.exists(p);
            boolean isDirectory = Files.isDirectory(p);
            boolean isReadable = Files.isReadable(p);
            
            if (!isPathAllowed(path)) {
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", "无权访问此路径"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "valid", exists && isDirectory && isReadable,
                "exists", exists,
                "isDirectory", isDirectory,
                "isReadable", isReadable,
                "path", path
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "error", e.getMessage()));
        }
    }
    
    private ResponseEntity<Map<String, Object>> getRootDirectories() {
        List<Map<String, Object>> roots = new ArrayList<>();
        File[] rootFiles = File.listRoots();
        
        for (File root : rootFiles) {
            String path = root.getAbsolutePath();
            if (isPathAllowed(path)) {
                roots.add(Map.of(
                    "name", root.getAbsolutePath(),
                    "path", root.getAbsolutePath(),
                    "isDirectory", true
                ));
            }
        }
        
        String os = System.getProperty("os.name").toLowerCase();
        String homeDir = System.getProperty("user.home");
        
        List<Map<String, Object>> quickAccess = new ArrayList<>();
        if (isPathAllowed(homeDir)) {
            quickAccess.add(Map.of("name", "主目录", "path", homeDir, "isDirectory", true));
        }
        if (os.contains("mac")) {
            String desktop = homeDir + "/Desktop";
            String documents = homeDir + "/Documents";
            if (new File(desktop).exists()) {
                quickAccess.add(Map.of("name", "桌面", "path", desktop, "isDirectory", true));
            }
            if (new File(documents).exists()) {
                quickAccess.add(Map.of("name", "文档", "path", documents, "isDirectory", true));
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "currentPath", "",
            "parentPath", "",
            "items", roots,
            "quickAccess", quickAccess
        ));
    }
    
    private boolean isPathAllowed(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String[] allowed = allowedBasePaths.split(",");
        String normalizedPath = path.toLowerCase();
        for (String allowedPath : allowed) {
            if (normalizedPath.startsWith(allowedPath.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
