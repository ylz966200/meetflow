CREATE TABLE IF NOT EXISTS weather_tool_config (
    config_key VARCHAR(64) PRIMARY KEY COMMENT '配置项名称',
    config_value VARCHAR(2048) NOT NULL COMMENT '配置项的值'
) COMMENT='天气工具配置表';

INSERT IGNORE INTO weather_tool_config (config_key, config_value) VALUES
('enabled', 'true'),
('geocoding_url', 'https://geocoding-api.open-meteo.com/v1/search?name={name}&count=5&language=zh&format=json'),
('forecast_url', 'https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto');
