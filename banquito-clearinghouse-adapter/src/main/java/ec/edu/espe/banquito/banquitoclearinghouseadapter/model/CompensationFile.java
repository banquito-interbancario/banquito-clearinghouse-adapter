package ec.edu.espe.banquito.banquitoclearinghouseadapter.model;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.enums.FileStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "clearing_compensation_file")
public class CompensationFile {
    @Id
    private String id;

    private UUID batchId;

    private String fileName;

    private String filePath;

    private String txtFilePath;

    private String pdfFilePath;

    private Integer offUsRecords;

    private BigDecimal totalAmount;

    private FileStatus status;

    private LocalDateTime generatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTxtFilePath() {
        return txtFilePath;
    }

    public void setTxtFilePath(String txtFilePath) {
        this.txtFilePath = txtFilePath;
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public void setPdfFilePath(String pdfFilePath) {
        this.pdfFilePath = pdfFilePath;
    }

    public Integer getOffUsRecords() {
        return offUsRecords;
    }

    public void setOffUsRecords(Integer offUsRecords) {
        this.offUsRecords = offUsRecords;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
