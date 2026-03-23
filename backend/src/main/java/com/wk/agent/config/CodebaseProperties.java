package com.wk.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.codebase")
public class CodebaseProperties {
    
    private List<ProjectInfo> projects = new ArrayList<>();
    
    public List<ProjectInfo> getProjects() {
        return projects;
    }
    
    public void setProjects(List<ProjectInfo> projects) {
        this.projects = projects;
    }
    
    public static class ProjectInfo {
        private String name;
        private String path;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
    }
}
