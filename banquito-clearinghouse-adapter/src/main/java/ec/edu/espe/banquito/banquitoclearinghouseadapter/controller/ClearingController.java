package ec.edu.espe.banquito.banquitoclearinghouseadapter.controller;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.dto.ClearingFileResponse;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.model.CompensationFile;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.repository.CompensationFileRepository;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.service.ClearingQueryService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

@RestController
@RequestMapping("/api/v2/clearing")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ClearingController {
    private final ClearingQueryService clearingQueryService;
    private final CompensationFileRepository compensationFileRepository;

    public ClearingController(ClearingQueryService clearingQueryService,
                              CompensationFileRepository compensationFileRepository) {
        this.clearingQueryService = clearingQueryService;
        this.compensationFileRepository = compensationFileRepository;
    }

    @GetMapping("/batches/{batchId}/file")
    public ResponseEntity<ClearingFileResponse> getfile(@PathVariable UUID batchId) {
        return ResponseEntity.ok(clearingQueryService.findByBatchId(batchId));
    }

    @GetMapping("/files")
    public List<CompensationFile> listFiles() {
        return compensationFileRepository.findAll().stream()
                .sorted(Comparator.comparing(CompensationFile::getGeneratedAt).reversed())
                .toList();
    }

    @GetMapping("/files/{id}/csv")
    public ResponseEntity<FileSystemResource> downloadCsv(@PathVariable String id) {
        return download(id, CompensationFile::getFilePath, "text/csv");
    }

    @GetMapping("/files/{id}/txt")
    public ResponseEntity<FileSystemResource> downloadTxt(@PathVariable String id) {
        return download(id, CompensationFile::getTxtFilePath, "text/plain");
    }

    @GetMapping("/files/{id}/pdf")
    public ResponseEntity<FileSystemResource> downloadPdf(@PathVariable String id) {
        return download(id, CompensationFile::getPdfFilePath, MediaType.APPLICATION_PDF_VALUE);
    }

    private ResponseEntity<FileSystemResource> download(String id,
                                                         java.util.function.Function<CompensationFile, String> pathExtractor,
                                                         String contentType) {
        CompensationFile compensationFile = compensationFileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado: " + id));
        String path = pathExtractor.apply(compensationFile);
        if (path == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo no tiene esa variante disponible");
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo ya no existe en disco: " + path);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }
}
