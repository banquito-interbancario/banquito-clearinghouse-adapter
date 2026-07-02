package ec.edu.espe.banquito.banquitoclearinghouseadapter.listener;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.service.OffUsConsumerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClearingQueueListener {
    private final OffUsConsumerService offUsConsumerService;

    public ClearingQueueListener(OffUsConsumerService offUsConsumerService) {
        this.offUsConsumerService = offUsConsumerService;
    }

    @KafkaListener(topics = "${app.kafka.clearing-topic}", groupId = "${spring.kafka.consumer.group-id:clearinghouse-adapter}")
    public void consume(OffUsPaymentMessage message){
        offUsConsumerService.process(message);
    }
}
