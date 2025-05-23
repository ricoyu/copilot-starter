package com.awesomecopilot.oauth2.converter;

import com.awesomecopilot.common.lang.utils.DateUtils;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalTime;

@ConfigurationPropertiesBinding
public class LocalTimeConverter implements Converter<String, LocalTime> {
	
	@Override
	public LocalTime convert(String source) {
		return DateUtils.toLocalTime(source);
	}
}
