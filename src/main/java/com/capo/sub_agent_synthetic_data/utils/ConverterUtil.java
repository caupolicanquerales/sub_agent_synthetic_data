package com.capo.sub_agent_synthetic_data.utils;

import org.springframework.http.codec.ServerSentEvent;

import com.capo.sub_agent_synthetic_data.response.DataMessage;


public class ConverterUtil {
		
	public static DataMessage setDataMessage(String data) {
		DataMessage dataMessage= new DataMessage();
		dataMessage.setMessage(data);
		return dataMessage;
	}
	
	public static ServerSentEvent<DataMessage> setServerSentEvent(DataMessage data, String eventName) {
		return ServerSentEvent.<DataMessage>builder()
				.id("1")
				.comment("prueba")
				.event(eventName)
			    .data(data)
			    .build();
	}
}
