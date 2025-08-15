package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.function.integration.dsl.FunctionFlowBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@SpringBootTest
class ReproducingReactiveFlowIssueApplicationTests {

	@TestConfiguration
	static class TestConfig {

		@Bean("myChannel")
		FluxMessageChannel myChannel() {
			return new FluxMessageChannel();
		}

		@Bean
		IntegrationFlow asyncFlow(
				@Qualifier("myChannel") MessageChannel myChannel,
				FunctionFlowBuilder functionFlowBuilder) {
			return functionFlowBuilder.from(myChannel)
					.apply("stepWithMessageMonoInput")
					.apply("stepWithMonoInput")
					.get();
		}

		@Bean
		Function<Message<Mono<Integer>>, Message<Mono<Integer>>> stepWithMessageMonoInput() {
			return messageMonoInteger -> {
				String passedHeaderValue = messageMonoInteger.getHeaders().get("someHeader", String.class);

				Mono<Integer> chainedMono = messageMonoInteger.getPayload()
						.map(i -> i * 2)
						.doOnNext(i -> System.out.println("stepWithMonoInput header: %s value: %d".formatted(passedHeaderValue, i)));

				return MessageBuilder.withPayload(chainedMono)
						.copyHeaders(messageMonoInteger.getHeaders())
						.build();
			};
		}

		@Bean
		Function<Mono<Integer>, Mono<Integer>> stepWithMonoInput() {
			return monoInteger -> monoInteger
					.map(i -> i * 2)
					.doOnNext(i -> System.out.println("stepWithMonoInput value: %d".formatted(i)));
		}
	}

	@Autowired
	FluxMessageChannel fluxMessageChannel;

	@Test
	void shouldSendMessageCorrectly() throws InterruptedException {
		Message<Integer> messageMonoInteger = MessageBuilder.withPayload(4)
				.setReplyChannelName(IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME)
				.setHeader("someHeader", ":)")
				.build();

		fluxMessageChannel.send(messageMonoInteger);

		Thread.sleep(2000); // let the async message finish logging
	}
}
