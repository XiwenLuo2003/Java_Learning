package com.example.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 交换机和队列。延迟队列的接法和 mall 的 RabbitMqConfig 一样：
 * 消息先进入带 TTL 的队列（没有消费者），过期后被投到死信交换机，再进真正被消费的队列。
 * <p>
 * mall 把 TTL 队列注释写成“死信队列”，容易混：死信交换机（DLX）是过期后的下一跳，TTL 队列自己不是死信队列。
 */
@Configuration
public class RabbitConfig {

    public static final String DIRECT = "demo.direct";
    public static final String DIRECT_QUEUE = "demo.direct.q";
    public static final String DIRECT_KEY = "order";

    public static final String FANOUT = "demo.fanout";
    public static final String FANOUT_Q1 = "demo.fanout.q1";
    public static final String FANOUT_Q2 = "demo.fanout.q2";

    public static final String TOPIC = "demo.topic";
    public static final String TOPIC_ORDER = "demo.topic.order";
    public static final String TOPIC_PAY = "demo.topic.pay";

    public static final String DELAY_EXCHANGE = "delay.direct";
    public static final String DELAY_TTL_QUEUE = "delay.ttl";
    public static final String DELAY_TTL_KEY = "delay.ttl";
    public static final String DELAY_READY_QUEUE = "delay.ready";
    public static final String DELAY_READY_KEY = "delay.ready";

    public static final String ORDER_EXCHANGE = "order.created.exchange";
    public static final String ORDER_QUEUE = "order.created";
    public static final String ORDER_KEY = "order.created";

    public static final String ACK_EXCHANGE = "demo.ack.exchange";
    public static final String ACK_QUEUE = "demo.ack";
    public static final String ACK_KEY = "demo.ack";
    public static final String ACK_DLQ = "demo.ack.dlq";
    public static final String ACK_DLQ_KEY = "demo.ack.dlq";

    @Bean
    DirectExchange directExchange() {
        return new DirectExchange(DIRECT, true, false);
    }

    @Bean
    Queue directQueue() {
        return QueueBuilder.durable(DIRECT_QUEUE).build();
    }

    @Bean
    Binding directBinding(DirectExchange directExchange, Queue directQueue) {
        return BindingBuilder.bind(directQueue).to(directExchange).with(DIRECT_KEY);
    }

    @Bean
    FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT, true, false);
    }

    @Bean
    Queue fanoutQueue1() {
        return QueueBuilder.durable(FANOUT_Q1).build();
    }

    @Bean
    Queue fanoutQueue2() {
        return QueueBuilder.durable(FANOUT_Q2).build();
    }

    @Bean
    Binding fanoutBinding1(FanoutExchange fanoutExchange, Queue fanoutQueue1) {
        return BindingBuilder.bind(fanoutQueue1).to(fanoutExchange);
    }

    @Bean
    Binding fanoutBinding2(FanoutExchange fanoutExchange, Queue fanoutQueue2) {
        return BindingBuilder.bind(fanoutQueue2).to(fanoutExchange);
    }

    @Bean
    TopicExchange topicExchange() {
        return new TopicExchange(TOPIC, true, false);
    }

    @Bean
    Queue topicOrderQueue() {
        return QueueBuilder.durable(TOPIC_ORDER).build();
    }

    @Bean
    Queue topicPayQueue() {
        return QueueBuilder.durable(TOPIC_PAY).build();
    }

    @Bean
    Binding topicOrderBinding(TopicExchange topicExchange, Queue topicOrderQueue) {
        return BindingBuilder.bind(topicOrderQueue).to(topicExchange).with("order.#");
    }

    @Bean
    Binding topicPayBinding(TopicExchange topicExchange, Queue topicPayQueue) {
        return BindingBuilder.bind(topicPayQueue).to(topicExchange).with("*.pay");
    }

    /** 过期消息的下一跳。对应 mall 的 orderDirect */
    @Bean
    DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    /**
     * 没有消费者。消息在这里等到 TTL，然后按 x-dead-letter-* 投到 delay.ready。
     * 不设队列级 x-message-ttl，每条消息用 expiration 属性（和 mall 的 CancelOrderSender 一样）。
     */
    @Bean
    Queue delayTtlQueue() {
        return QueueBuilder.durable(DELAY_TTL_QUEUE)
                .deadLetterExchange(DELAY_EXCHANGE)
                .deadLetterRoutingKey(DELAY_READY_KEY)
                .build();
    }

    @Bean
    Queue delayReadyQueue() {
        return QueueBuilder.durable(DELAY_READY_QUEUE).build();
    }

    @Bean
    Binding delayTtlBinding(DirectExchange delayExchange, Queue delayTtlQueue) {
        return BindingBuilder.bind(delayTtlQueue).to(delayExchange).with(DELAY_TTL_KEY);
    }

    @Bean
    Binding delayReadyBinding(DirectExchange delayExchange, Queue delayReadyQueue) {
        return BindingBuilder.bind(delayReadyQueue).to(delayExchange).with(DELAY_READY_KEY);
    }

    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE).build();
    }

    @Bean
    Binding orderBinding(DirectExchange orderExchange, Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(ORDER_KEY);
    }

    @Bean
    DirectExchange ackExchange() {
        return new DirectExchange(ACK_EXCHANGE, true, false);
    }

    /** 消费失败且不再入队时，进入死信队列，避免毒消息反复重试堵住队列 */
    @Bean
    Queue ackQueue() {
        return QueueBuilder.durable(ACK_QUEUE)
                .deadLetterExchange(ACK_EXCHANGE)
                .deadLetterRoutingKey(ACK_DLQ_KEY)
                .build();
    }

    @Bean
    Queue ackDlq() {
        return QueueBuilder.durable(ACK_DLQ).build();
    }

    @Bean
    Binding ackBinding(DirectExchange ackExchange, Queue ackQueue) {
        return BindingBuilder.bind(ackQueue).to(ackExchange).with(ACK_KEY);
    }

    @Bean
    Binding ackDlqBinding(DirectExchange ackExchange, Queue ackDlq) {
        return BindingBuilder.bind(ackDlq).to(ackExchange).with(ACK_DLQ_KEY);
    }

    @Bean
    RabbitTemplate configuredTemplate(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }
}
