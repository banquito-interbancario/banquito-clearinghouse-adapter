package ec.edu.espe.banquito.banquitoclearinghouseadapter.config;

import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.clearing.queue}")
    private String queueName;

    @Value("${rabbitmq.clearing.exchange:clearing.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.clearing.routing-key:clearing.outbound}")
    private String routingKey;

    @Bean
    public Queue clearingOutboundQueue() {
        return new Queue(queueName, true);
    }

    // Cola consumida por ClearingQueryService.consume(); se declara aqui para
    // que exista en cualquier broker (sin esto, un broker nuevo no la crea).
    @Bean
    public Queue clearingQueryQueue() {
        return new Queue("clearing-query-queue", true);
    }

    @Bean
    public DirectExchange clearingExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Binding clearingOutboundBinding(Queue clearingOutboundQueue, DirectExchange clearingExchange) {
        return BindingBuilder.bind(clearingOutboundQueue).to(clearingExchange).with(routingKey);
    }

    // RF-03: el routing-service publica JSON; sin este converter, Spring AMQP
    // intentaría deserializar con SimpleMessageConverter (Java serialization) y fallaría.
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
