package com.capo.sub_agent_synthetic_data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.capo.sub_agent_synthetic_data.request.GenerationSyntheticDataRequest;
import com.capo.sub_agent_synthetic_data.response.DataMessage;
import com.capo.sub_agent_synthetic_data.utils.ConverterUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ExecutingAgentService {

	private static final Logger log = LoggerFactory.getLogger(ExecutingAgentService.class);

	private final ChatClient chatClient;
	private final String systemPrompt;
	private final ReactiveStringRedisTemplate redisTemplate;
	
	@Value(value="${event.name-chat}")
	private String eventName;
	
	public ExecutingAgentService(@Qualifier("chatClientGeneral") ChatClient chatClient,
			@Qualifier("systemPrompt") String systemPrompt,
			ReactiveStringRedisTemplate redisTemplate) {
		this.chatClient = chatClient;
		this.systemPrompt= systemPrompt;
		this.redisTemplate = redisTemplate;
	}
	
	public Flux<ServerSentEvent<DataMessage>> executing(GenerationSyntheticDataRequest request) {
		String jsonKey = (request.getImageReferences() != null && !request.getImageReferences().isEmpty())
				? request.getImageReferences().get(0) : null;
		String baseUserMessage = "[INPUT_PROMPT_USER: RAW_PROMPT_USER] " + request.getPrompt();

		Mono<String> userMessageMono = (jsonKey != null)
				? redisTemplate.opsForValue().get(jsonKey)
						.map(stored -> baseUserMessage + "\n[INPUT_DATA: RAW_DATA] " + stored)
						.defaultIfEmpty(baseUserMessage)
				: Mono.just(baseUserMessage);

	    return userMessageMono.flatMapMany(userMessage ->
	    	chatClient.prompt()
	    		.messages(new SystemMessage(systemPrompt))
	    		.user(userMessage)
                .stream()
                .chatResponse()
                .map(this::getTokenMessage)
				.filter(content -> !content.isEmpty())
                .map(ConverterUtil::setDataMessage)
                .map(data -> ConverterUtil.setServerSentEvent(data, eventName))
                .doOnComplete(() -> log.info("AI Stream Finished. Sending completion flag..."))
        	    .doOnTerminate(() -> log.info("HTTP Response fully closed on server"))
        	    .onErrorResume(WebClientResponseException.class, e -> {
        	        String errorBody = e.getResponseBodyAsString();
        	        log.error("OpenAI 400 Error Body: {}", e);
        	        return Flux.error(new RuntimeException("OpenAI API call failed: " + errorBody, e));
        	    })
	    );
	}
	
	
	
	private String getTokenMessage(ChatResponse chatResponse) {
	    if (chatResponse == null || chatResponse.getResult() == null) {
	        return "";
	    }
	    String content = chatResponse.getResult().getOutput().getText();
	    return (content != null) ? content : "";
	}
}
