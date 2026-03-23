package com.wk.agent.domain.valueobject;

public class AgentResult {
    private String message;
    private boolean success;
    private String errorMessage;

    public AgentResult(String message, boolean success) {
        this.message = message;
        this.success = success;
    }

    public AgentResult(String message, boolean success, String errorMessage) {
        this.message = message;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}