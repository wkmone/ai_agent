package com.wk.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气服务工具
 */
@Component
public class WeatherTool {
    
    @Tool(description = "获取指定城市的天气信息")
    public String getWeather(
            @ToolParam(description = "城市名称") String city) {
        // 模拟天气数据
        // 实际应用中可以调用天气API获取真实数据
        return String.format("城市: %s\n天气: 晴\n温度: 25°C\n湿度: 61%%\n风力: 微风", city);
    }
}