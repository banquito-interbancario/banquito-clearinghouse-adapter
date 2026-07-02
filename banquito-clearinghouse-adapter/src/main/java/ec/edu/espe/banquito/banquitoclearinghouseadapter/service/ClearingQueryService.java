package ec.edu.espe.banquito.banquitoclearinghouseadapter.service;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.dto.ClearingFileResponse;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.exception.BatchNotFoundException;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.model.CompensationFile;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.repository.CompensationFileRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClearingQueryService {
    private final OffUsConsumerService offUsConsumerService;
    private final CompensationFileRepository compensationFileRepository;
    public ClearingQueryService(OffUsConsumerService offUsConsumerService, CompensationFileRepository compensationFileRepository) {
        this.offUsConsumerService = offUsConsumerService;
        this.compensationFileRepository = compensationFileRepository;
    }

    @KafkaListener(topics = "clearing-query", groupId = "${spring.kafka.consumer.group-id:clearinghouse-adapter}")
    public void consume(OffUsPaymentMessage message){
        offUsConsumerService.process(message);
    }

    public ClearingFileResponse findByBatchId(UUID batchId) {

        CompensationFile file = compensationFileRepository
                .findByBatchId(batchId)
                .orElseThrow(() ->
                        new BatchNotFoundException(
                                "No existe el lote: " + batchId));

        ClearingFileResponse response =
                new ClearingFileResponse();

        response.setBatchId(file.getBatchId());
        response.setFileName(file.getFileName());
        response.setFilePath(file.getFilePath());
        response.setOffUsRecords(file.getOffUsRecords());
        response.setTotalOffUsAmount(file.getTotalAmount());
        response.setStatus(file.getStatus().name());
        response.setGeneratedAt(file.getGeneratedAt());

        return response;
    }
}
