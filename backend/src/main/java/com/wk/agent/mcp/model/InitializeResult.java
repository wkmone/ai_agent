package com.wk.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializeResult {
    private String protocolVersion;
    private Capabilities capabilities;
    private ServerInfo serverInfo;
    private Map<String, Object> metadata;

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Capabilities {
        private Tools tools;
        private Resources resources;
        private Prompts prompts;
        private Map<String, Object> experimental;

        public Tools getTools() {
            return tools;
        }

        public void setTools(Tools tools) {
            this.tools = tools;
        }

        public Resources getResources() {
            return resources;
        }

        public void setResources(Resources resources) {
            this.resources = resources;
        }

        public Prompts getPrompts() {
            return prompts;
        }

        public void setPrompts(Prompts prompts) {
            this.prompts = prompts;
        }

        public Map<String, Object> getExperimental() {
            return experimental;
        }

        public void setExperimental(Map<String, Object> experimental) {
            this.experimental = experimental;
        }
    }

    public static class Tools {
        private boolean listChanged;

        public boolean isListChanged() {
            return listChanged;
        }

        public void setListChanged(boolean listChanged) {
            this.listChanged = listChanged;
        }
    }

    public static class Resources {
        private boolean listChanged;
        private boolean subscribe;

        public boolean isListChanged() {
            return listChanged;
        }

        public void setListChanged(boolean listChanged) {
            this.listChanged = listChanged;
        }

        public boolean isSubscribe() {
            return subscribe;
        }

        public void setSubscribe(boolean subscribe) {
            this.subscribe = subscribe;
        }
    }

    public static class Prompts {
        private boolean listChanged;

        public boolean isListChanged() {
            return listChanged;
        }

        public void setListChanged(boolean listChanged) {
            this.listChanged = listChanged;
        }
    }

    public static class ServerInfo {
        private String name;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
