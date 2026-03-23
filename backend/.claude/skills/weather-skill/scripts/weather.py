#!/usr/bin/env python3
"""
增强版天气查询脚本
使用 wttr.in API 获取全球城市天气信息
支持中英文城市名、多城市查询、格式化输出
"""

import sys
import urllib.request
import json
from datetime import datetime
import argparse
import time
import requests


def get_weather(city: str) -> dict:
    """
    获取单个城市的天气信息
    
    Args:
        city: 城市名称（支持中英文）
    
    Returns:
        dict: 包含天气信息的字典，包含以下键：
            - city: 城市名
            - weather: 天气描述
            - temp_c: 摄氏度温度
            - feelslike_c: 体感温度
            - humidity: 湿度百分比
            - wind_dir: 风向
            - wind_speed: 风速(km/h)
            - observation_time: 观测时间
            - success: 是否成功
            - error: 错误信息（如果有）
    """
    try:
        # URL编码城市名
        encoded_city = urllib.parse.quote(city)
        url = f"https://wttr.in/{encoded_city}?format=j1&lang=zh"
        
        # 设置请求头，避免被限制
        headers = {
            'User-Agent': 'Mozilla/5.0 (compatible; WeatherBot/1.0)'
        }
        
        response = requests.request("GET", url, headers=headers)

        if response.status_code != 200:
            return {
                "success": False,
                "error": f"API返回状态码: {response.status}",
                "city": city
            }

        data = response.json().get("data")
        
        # 提取当前天气信息
        current = data.get("current_condition", [{}])[0]
        area = data.get("nearest_area", [{}])[0]
        
        # 提取观测时间
        observation_time = current.get("observation_time", "")
        
        # 格式化结果
        result = {
            "success": True,
            "city": city,
            "weather": current.get("weatherDesc", [{}])[0].get("value", "未知"),
            "temp_c": current.get("temp_C", "N/A"),
            "feelslike_c": current.get("FeelsLikeC", "N/A"),
            "humidity": current.get("humidity", "N/A"),
            "wind_dir": current.get("winddir16Point", "未知"),
            "wind_speed": current.get("windspeedKmph", "N/A"),
            "observation_time": observation_time,
            "local_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
        
        return result
        
    except urllib.error.URLError as e:
        return {
            "success": False,
            "error": f"网络错误: {str(e)}",
            "city": city
        }
    except json.JSONDecodeError as e:
        return {
            "success": False,
            "error": f"数据解析错误: {str(e)}",
            "city": city
        }
    except Exception as e:
        return {
            "success": False,
            "error": f"未知错误: {str(e)}",
            "city": city
        }


def format_weather_result(weather_data: dict) -> str:
    """
    格式化天气结果
    
    Args:
        weather_data: 天气数据字典
    
    Returns:
        str: 格式化的天气字符串
    """
    if not weather_data.get("success", False):
        error_msg = weather_data.get("error", "未知错误")
        return f"❌ 查询 {weather_data.get('city', '未知城市')} 天气失败: {error_msg}"
    
    # 天气图标映射
    weather_icons = {
        "晴": "☀️",
        "晴朗": "☀️",
        "多云": "⛅",
        "阴": "☁️",
        "雨": "🌧️",
        "小雨": "🌦️",
        "中雨": "🌧️",
        "大雨": "⛈️",
        "雷阵雨": "⛈️",
        "雪": "❄️",
        "小雪": "🌨️",
        "中雪": "🌨️",
        "大雪": "❄️",
        "雾": "🌫️",
        "雾霾": "😷"
    }
    
    # 根据天气描述选择图标
    weather_desc = weather_data["weather"]
    icon = "🌤️"  # 默认图标
    for key, value in weather_icons.items():
        if key in weather_desc:
            icon = value
            break
    
    # 格式化输出
    result = []
    result.append(f"{icon} {weather_data['city']} 天气报告")
    result.append(f"🌡️ 温度: {weather_data['temp_c']}°C")
    result.append(f"🤏 体感温度: {weather_data['feelslike_c']}°C")
    result.append(f"💧 湿度: {weather_data['humidity']}%")
    result.append(f"🌬️ 风力: {weather_data['wind_dir']} {weather_data['wind_speed']} km/h")
    result.append(f"⏰ 观测时间: {weather_data.get('observation_time', '未知')}")
    
    # 添加穿衣建议
    temp = int(weather_data['temp_c']) if weather_data['temp_c'].isdigit() else 20
    clothing_suggestion = get_clothing_suggestion(temp)
    result.append(f"👔 穿衣建议: {clothing_suggestion}")
    
    return "\n".join(result)


def get_clothing_suggestion(temperature: int) -> str:
    """
    根据温度提供穿衣建议
    
    Args:
        temperature: 温度(摄氏度)
    
    Returns:
        str: 穿衣建议
    """
    if temperature >= 30:
        return "轻薄短袖、短裤，注意防晒"
    elif temperature >= 25:
        return "短袖、薄长裤或短裙"
    elif temperature >= 20:
        return "长袖T恤、薄外套"
    elif temperature >= 15:
        return "长袖、薄毛衣、夹克"
    elif temperature >= 10:
        return "毛衣、厚外套"
    elif temperature >= 5:
        return "棉衣、厚毛衣、围巾"
    elif temperature >= 0:
        return "羽绒服、厚毛衣、帽子手套"
    else:
        return "加厚羽绒服、保暖内衣、帽子围巾手套"


def get_weather_multiple(cities: list) -> str:
    """
    查询多个城市的天气
    
    Args:
        cities: 城市名称列表
    
    Returns:
        str: 所有城市天气的汇总报告
    """
    if not cities:
        return "❌ 未提供城市名称"
    
    results = []
    for city in cities:
        # 防止请求过快
        time.sleep(0.5)
        weather_data = get_weather(city.strip())
        formatted = format_weather_result(weather_data)
        results.append(formatted)
        results.append("")  # 添加空行分隔
    
    return "\n".join(results)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="查询城市天气")
    parser.add_argument("cities", nargs="+", help="城市名称，支持多个城市用空格分隔")
    parser.add_argument("--raw", action="store_true", help="输出原始JSON格式")
    
    args = parser.parse_args()
    
    if not args.cities:
        print("错误: 请提供至少一个城市名称")
        sys.exit(1)
    
    if len(args.cities) == 1:
        # 单个城市查询
        weather_data = get_weather(args.cities[0])
        
        if args.raw:
            print(json.dumps(weather_data, ensure_ascii=False, indent=2))
        else:
            print(format_weather_result(weather_data))
    else:
        # 多个城市查询
        if args.raw:
            all_data = []
            for city in args.cities:
                weather_data = get_weather(city.strip())
                all_data.append(weather_data)
                time.sleep(0.5)
            print(json.dumps(all_data, ensure_ascii=False, indent=2))
        else:
            print("🌍 多城市天气报告")
            print("=" * 40)
            print(get_weather_multiple(args.cities))


if __name__ == "__main__":
    main()