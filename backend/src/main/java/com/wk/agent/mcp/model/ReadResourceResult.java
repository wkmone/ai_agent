package com.wk.agent.mcp.model;

import java.util.List;

public class ReadResourceResult {
    private List<Content> contents;

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }

    public static class Content {
        private String uri;
        private String mimeType;
        private String text;
        private String blob;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getBlob() {
            return blob;
        }

        public void setBlob(String blob) {
            this.blob = blob;
        }
    }
}
