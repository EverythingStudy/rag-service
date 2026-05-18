package cn.project.base.agentruntime.skill.builtin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 天气查询技能 —— 基于 Open-Meteo 免费 API。
 */
@Service
public class WeatherSkill {

    private static final String BASE_URL = "https://api.open-meteo.com/v1";

    private final RestClient restClient;

    public WeatherSkill() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "AgentRuntime/1.0")
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherData(
            @JsonProperty("current") CurrentWeather current,
            @JsonProperty("daily") DailyForecast daily,
            @JsonProperty("current_units") CurrentUnits currentUnits) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CurrentWeather(
                @JsonProperty("temperature_2m") Double temperature,
                @JsonProperty("apparent_temperature") Double feelsLike,
                @JsonProperty("relative_humidity_2m") Integer humidity,
                @JsonProperty("weather_code") Integer weatherCode,
                @JsonProperty("wind_speed_10m") Double windSpeed,
                @JsonProperty("wind_direction_10m") Integer windDirection) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CurrentUnits(
                @JsonProperty("temperature_2m") String temperatureUnit,
                @JsonProperty("wind_speed_10m") String windSpeedUnit) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DailyForecast(
                @JsonProperty("time") List<String> time,
                @JsonProperty("temperature_2m_max") List<Double> tempMax,
                @JsonProperty("temperature_2m_min") List<Double> tempMin,
                @JsonProperty("weather_code") List<Integer> weatherCode,
                @JsonProperty("precipitation_sum") List<Double> precipitationSum,
                @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax) {}
    }

    @Tool(description = "获取指定经纬度的天气信息，包括当前温度和未来天气预报")
    public String getWeather(
            @ToolParam(description = "纬度，例如北京约 39.9") double latitude,
            @ToolParam(description = "经度，例如北京约 116.4") double longitude) {

        var data = restClient.get()
                .uri("/forecast?latitude={lat}&longitude={lon}"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m"
                        + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_sum,wind_speed_10m_max"
                        + "&timezone=auto&forecast_days=3",
                        latitude, longitude)
                .retrieve()
                .body(WeatherData.class);
        if (data == null) return "无法获取天气数据";

        var sb = new StringBuilder();
        var cur = data.current();
        var units = data.currentUnits();

        sb.append(String.format("当前温度: %.1f%s (体感 %.1f%s)%n",
                cur.temperature(), units.temperatureUnit(),
                cur.feelsLike(), units.temperatureUnit()));
        sb.append(String.format("湿度: %d%% | 风速: %.1f %s%n",
                cur.humidity(), cur.windSpeed(), units.windSpeedUnit()));
        sb.append(String.format("天气: %s%n%n", weatherCodeToText(cur.weatherCode())));

        if (data.daily() != null) {
            sb.append("未来预报:\n");
            var daily = data.daily();
            for (int i = 0; i < daily.time().size(); i++) {
                String date = LocalDate.parse(daily.time().get(i))
                        .format(DateTimeFormatter.ofPattern("MM-dd (EEE)"));
                sb.append(String.format("  %s: %.0f~%.0f°C %s%n",
                        date, daily.tempMin().get(i), daily.tempMax().get(i),
                        weatherCodeToText(daily.weatherCode().get(i))));
            }
        }
        return sb.toString();
    }

    private static String weatherCodeToText(int code) {
        return switch (code) {
            case 0 -> "晴朗";
            case 1, 2, 3 -> "多云";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 61, 63, 65 -> "雨";
            case 71, 73, 75 -> "雪";
            case 80, 81, 82 -> "阵雨";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴+冰雹";
            default -> "未知";
        };
    }
}
