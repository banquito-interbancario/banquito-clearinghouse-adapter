package ec.edu.espe.banquito.banquitoclearinghouseadapter.service;

import ec.edu.espe.banquito.banquitoclearinghouseadapter.enums.PaymentStatus;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.exception.AccountingIntegrationException;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.exception.FileGenerationException;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.model.CompensationFile;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.model.OffUsPayment;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.repository.CompensationFileRepository;
import ec.edu.espe.banquito.banquitoclearinghouseadapter.repository.OffUsPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CompensationFileService {

    private static final Logger log = LoggerFactory.getLogger(CompensationFileService.class);
    private static final String SPI_FILE_PREFIX = "SPI_BCE_";
    private static final String CONSOLIDADO_FILE_PREFIX = "CONSOLIDADO_BCE_";
    private static final ZoneId APP_ZONE = ZoneId.of("America/Guayaquil");

    private final OffUsPaymentRepository offUsPaymentRepository;
    private final CompensationFileRepository compensationFileRepository;
    private final AccountingService accountingService;
    private final String outputDir;

    public CompensationFileService(
            OffUsPaymentRepository offUsPaymentRepository,
            CompensationFileRepository compensationFileRepository,
            AccountingService accountingService,
            @Value("${compensation.output.dir:}") String outputDir) {

        this.offUsPaymentRepository = offUsPaymentRepository;
        this.compensationFileRepository = compensationFileRepository;
        this.accountingService = accountingService;
        this.outputDir = outputDir != null ? outputDir.trim() : "";
    }

    public CompensationFile generateCompensationFile(UUID batchId) {
        List<OffUsPayment> payments = offUsPaymentRepository.findByBatchId(batchId);

        if (payments == null || payments.isEmpty()) {
            throw new FileGenerationException(
                    "No hay pagos Off-Us para el lote: " + batchId
            );
        }

        int count = payments.size();

        BigDecimal total = payments.stream()
                .map(OffUsPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String fileName = buildFileName(batchId);

        try {
            File dir = resolveOutputDirectory();
            File out = writeCompensationFile(dir, fileName, payments);

            CompensationFile file = buildCompensationFile(
                    batchId,
                    out,
                    count,
                    total
            );

            compensationFileRepository.save(file);

            callAccountingService(batchId, total);

            return file;

        } catch (IOException e) {
            throw new FileGenerationException(
                    "No se pudo generar el archivo de compensación: "
                            + e.getMessage(),
                    e
            );
        }
    }

    @Scheduled(fixedRate = 30000)
    public void generateSpiFile() {
        List<OffUsPayment> pendingPayments = offUsPaymentRepository.findByStatus(PaymentStatus.RECEIVED);
        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("Generando archivo SPI para el Banco Central con {} transacciones...", pendingPayments.size());

        try {
            File dir = resolveOutputDirectory();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = LocalDateTime.now(APP_ZONE).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File csvFile = new File(dir, SPI_FILE_PREFIX + timestamp + ".csv");
            File txtFile = new File(dir, SPI_FILE_PREFIX + timestamp + ".txt");
            File pdfFile = new File(dir, SPI_FILE_PREFIX + timestamp + ".pdf");

            writeSpiCsv(csvFile, pendingPayments);
            writeSpiTxt(txtFile, pendingPayments);
            writeSpiPdf(pdfFile, pendingPayments, timestamp);

            BigDecimal total = pendingPayments.stream()
                    .map(OffUsPayment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            UUID cycleId = UUID.randomUUID();
            CompensationFile file = new CompensationFile();
            file.setBatchId(cycleId);
            file.setFileName(csvFile.getName());
            file.setFilePath(csvFile.getAbsolutePath());
            file.setTxtFilePath(txtFile.getAbsolutePath());
            file.setPdfFilePath(pdfFile.getAbsolutePath());
            file.setOffUsRecords(pendingPayments.size());
            file.setTotalAmount(total);
            file.setStatus(ec.edu.espe.banquito.banquitoclearinghouseadapter.enums.FileStatus.GENERATED);
            file.setGeneratedAt(LocalDateTime.now(APP_ZONE));
            file.setFileType("CICLO");
            compensationFileRepository.save(file);

            for (OffUsPayment payment : pendingPayments) {
                payment.setStatus(PaymentStatus.ACCOUNTED);
            }
            offUsPaymentRepository.saveAll(pendingPayments);

            log.info("Archivo SPI generado exitosamente: {} / {} / {}",
                    csvFile.getName(), txtFile.getName(), pdfFile.getName());

        } catch (Exception e) {
            log.error("Error generando el archivo SPI: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${compensation.consolidation.cron:0 0 20 * * *}")
    public void generateScheduledConsolidatedFile() {
        generateConsolidatedFile(LocalDate.now(APP_ZONE));
    }

    public CompensationFile generateConsolidatedFile(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        // El consolidado es UNO por día, pero se puede regenerar varias veces durante el día
        // para reflejar movimientos nuevos: cada regeneración sobrescribe el mismo registro
        // (mismo archivo en disco) en vez de crear uno aparte. Si quedaron duplicados de
        // pruebas anteriores, se conserva el más reciente y se eliminan los demás.
        List<CompensationFile> existingForDay = compensationFileRepository
                .findAllByFileTypeAndPeriodFrom("CONSOLIDADO", from);
        CompensationFile file = existingForDay.stream()
                .max(java.util.Comparator.comparing(CompensationFile::getGeneratedAt))
                .orElseGet(CompensationFile::new);
        existingForDay.stream()
                .filter(f -> !f.getId().equals(file.getId()))
                .forEach(compensationFileRepository::delete);

        List<OffUsPayment> payments = offUsPaymentRepository.findByCreatedAtBetween(from, to);
        if (payments.isEmpty()) {
            throw new FileGenerationException(
                    "No hay movimientos interbancarios registrados el " + date
            );
        }

        log.info("Generando/actualizando archivo consolidado del Banco Central para {} con {} transacciones...",
                date, payments.size());

        try {
            File dir = resolveOutputDirectory();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String datePart = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            File csvFile = new File(dir, CONSOLIDADO_FILE_PREFIX + datePart + ".csv");
            File txtFile = new File(dir, CONSOLIDADO_FILE_PREFIX + datePart + ".txt");
            File pdfFile = new File(dir, CONSOLIDADO_FILE_PREFIX + datePart + ".pdf");

            writeSpiCsv(csvFile, payments);
            writeSpiTxt(txtFile, payments);
            writeSpiPdf(pdfFile, payments, "Consolidado " + datePart);

            BigDecimal total = payments.stream()
                    .map(OffUsPayment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (file.getBatchId() == null) {
                file.setBatchId(UUID.randomUUID());
            }
            file.setFileName(csvFile.getName());
            file.setFilePath(csvFile.getAbsolutePath());
            file.setTxtFilePath(txtFile.getAbsolutePath());
            file.setPdfFilePath(pdfFile.getAbsolutePath());
            file.setOffUsRecords(payments.size());
            file.setTotalAmount(total);
            file.setStatus(ec.edu.espe.banquito.banquitoclearinghouseadapter.enums.FileStatus.GENERATED);
            file.setGeneratedAt(LocalDateTime.now(APP_ZONE));
            file.setFileType("CONSOLIDADO");
            file.setPeriodFrom(from);
            file.setPeriodTo(to);
            compensationFileRepository.save(file);

            log.info("Archivo consolidado generado exitosamente: {} / {} / {}",
                    csvFile.getName(), txtFile.getName(), pdfFile.getName());

            return file;
        } catch (Exception e) {
            throw new FileGenerationException(
                    "No se pudo generar el archivo consolidado: " + e.getMessage(), e
            );
        }
    }

    private void writeSpiCsv(File file, List<OffUsPayment> payments) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("TRX_ID,ROUTING_CODE,ORIGIN_ACCOUNT,DESTINATION_ACCOUNT,AMOUNT,DATE");
            for (OffUsPayment payment : payments) {
                writer.printf("%s,%s,%s,%s,%.2f,%s%n",
                        payment.getTransactionId(),
                        payment.getRoutingCode(),
                        payment.getOriginAccount(),
                        payment.getDestinationAccount(),
                        payment.getAmount(),
                        payment.getCreatedAt()
                );
            }
        }
    }

    private void writeSpiTxt(File file, List<OffUsPayment> payments) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("transactionId|routingCode|originAccount|destinationAccount|amount|currency|valueDate");
            writer.newLine();
            for (OffUsPayment payment : payments) {
                writer.write(String.join("|",
                        String.valueOf(payment.getTransactionId()),
                        String.valueOf(payment.getRoutingCode()),
                        String.valueOf(payment.getOriginAccount()),
                        String.valueOf(payment.getDestinationAccount()),
                        payment.getAmount() != null ? payment.getAmount().toPlainString() : "0",
                        String.valueOf(payment.getCurrency()),
                        String.valueOf(payment.getValueDate())
                ));
                writer.newLine();
            }
        }
    }

    private void writeSpiPdf(File file, List<OffUsPayment> payments, String timestamp) throws java.io.FileNotFoundException, com.lowagie.text.DocumentException {
        com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate());
        try {
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new java.io.FileOutputStream(file));
            document.open();

            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 16);
            com.lowagie.text.Font headerFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, java.awt.Color.WHITE);
            com.lowagie.text.Font rowFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 9);

            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Archivo SPI — Banco Central (Ciclo " + timestamp + ")", titleFont);
            title.setSpacingAfter(12);
            document.add(title);

            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(6);
            table.setWidthPercentage(100);
            for (String h : new String[]{"TRX ID", "Routing", "Cuenta origen", "Cuenta destino", "Monto", "Fecha"}) {
                com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(h, headerFont));
                cell.setBackgroundColor(new java.awt.Color(30, 58, 138));
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (OffUsPayment payment : payments) {
                table.addCell(new com.lowagie.text.Phrase(String.valueOf(payment.getTransactionId()), rowFont));
                table.addCell(new com.lowagie.text.Phrase(String.valueOf(payment.getRoutingCode()), rowFont));
                table.addCell(new com.lowagie.text.Phrase(String.valueOf(payment.getOriginAccount()), rowFont));
                table.addCell(new com.lowagie.text.Phrase(String.valueOf(payment.getDestinationAccount()), rowFont));
                table.addCell(new com.lowagie.text.Phrase("$" + payment.getAmount(), rowFont));
                table.addCell(new com.lowagie.text.Phrase(String.valueOf(payment.getCreatedAt()), rowFont));
            }
            document.add(table);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private String buildFileName(UUID batchId) {
        String datePart = LocalDate.now(APP_ZONE)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return String.format(
                "COMPENSACION_%s_%s.txt",
                datePart,
                batchId
        );
    }

    private File resolveOutputDirectory() throws IOException {
        if (!outputDir.isBlank()) {
            return createConfiguredDirectory();
        }
        return createTemporaryDirectory();
    }

    private File createConfiguredDirectory() {
        Path configured = Path.of(outputDir);

        if (configured.getParent() == null) {
            throw new FileGenerationException(
                    "Directorio de salida inválido: no puede ser la raíz del sistema"
            );
        }

        File dir = configured.toFile();

        if (!dir.exists() && !dir.mkdirs()) {
            throw new FileGenerationException(
                    "No se pudo crear el directorio de salida: "
                            + dir.getAbsolutePath()
            );
        }

        validateWritableDirectory(
                dir,
                "El directorio de salida no es escribible: "
        );

        return dir;
    }

    private File createTemporaryDirectory() throws IOException {
        Path userBase = Path.of(
                System.getProperty("user.home"),
                ".banquito",
                "compensation"
        );

        Files.createDirectories(userBase);

        File dir = Files.createTempDirectory(
                userBase,
                "compensation-"
            ).toFile();

        validateWritableDirectory(
                dir,
                "El directorio temporal no es escribible: "
        );

        return dir;
    }

    private void validateWritableDirectory(File dir, String message) {
        if (!dir.canWrite()) {
            throw new FileGenerationException(
                    message + dir.getAbsolutePath()
            );
        }
    }

    private File writeCompensationFile(
            File dir,
            String fileName,
            List<OffUsPayment> payments) throws IOException {

        File out = new File(dir, fileName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {

            writer.write(
                    "transactionId|routingCode|originAccount|destinationAccount|amount|currency|valueDate|concept"
            );
            writer.newLine();

            for (OffUsPayment payment : payments) {
                writer.write(buildLine(payment));
                writer.newLine();
            }
        }

        return out;
    }

    private CompensationFile buildCompensationFile(
            UUID batchId,
            File out,
            int count,
            BigDecimal total) {

        CompensationFile file = new CompensationFile();

        file.setBatchId(batchId);
        file.setFileName(out.getName());
        file.setFilePath(out.getAbsolutePath());
        file.setOffUsRecords(count);
        file.setTotalAmount(total);
        file.setStatus(
                ec.edu.espe.banquito.banquitoclearinghouseadapter.enums.FileStatus.GENERATED
        );
        file.setGeneratedAt(LocalDateTime.now(APP_ZONE));
        file.setFileType("LOTE");

        return file;
    }

    private void callAccountingService(UUID batchId, BigDecimal total) {
        try {
            accountingService.registerOffUsAccountingEntry(batchId, total);
        } catch (Exception ex) {
            throw new AccountingIntegrationException(
                    "Error registrando asiento contable: " + ex.getMessage(),
                    ex
            );
        }
    }

    private String buildLine(OffUsPayment p) {
        String txId = p.getTransactionId() != null
                ? p.getTransactionId().toString()
                : "";

        String routing = p.getRoutingCode() != null
                ? p.getRoutingCode()
                : "";

        String origin = p.getOriginAccount() != null
                ? p.getOriginAccount()
                : "";

        String dest = p.getDestinationAccount() != null
                ? p.getDestinationAccount()
                : "";

        String amount = p.getAmount() != null
                ? p.getAmount().toPlainString()
                : "0";

        String currency = p.getCurrency() != null
                ? p.getCurrency()
                : "";

        String valueDate = p.getValueDate() != null
                ? p.getValueDate().toString()
                : "";

        String concept = p.getConcept() != null
                ? p.getConcept()
                : "";

        return String.join(
                "|",
                txId,
                routing,
                origin,
                dest,
                amount,
                currency,
                valueDate,
                concept
        );
    }
}
