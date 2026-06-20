package ec.edu.espe.banquito.banquitoclearinghouseadapter.repository;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.model.CompensationFile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CompensationFileRepository extends MongoRepository<CompensationFile,String> {
    Optional<CompensationFile> findByBatchId(UUID batchId);
    Optional<CompensationFile> findByFileTypeAndPeriodFrom(String fileType, LocalDateTime periodFrom);
}
