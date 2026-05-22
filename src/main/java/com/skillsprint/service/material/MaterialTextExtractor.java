package com.skillsprint.service.material;

import com.skillsprint.enums.material.FileType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class MaterialTextExtractor {

    public ExtractedText extract(byte[] fileBytes, FileType fileType) {
        try {
            return switch (fileType) {
                case PDF -> extractPdf(fileBytes);
                case DOCX -> extractDocx(fileBytes);
                case PPTX -> extractPptx(fileBytes);
                case TXT -> new ExtractedText(new String(fileBytes, StandardCharsets.UTF_8), null);
                case ZIP -> throw new AppException(
                        ErrorCode.MATERIAL_PROCESSING_FAILED,
                        "ZIP chưa hỗ trợ đọc nội dung trong MVP"
                );
            };
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.MATERIAL_PROCESSING_FAILED);
        }
    }

    private ExtractedText extractPdf(byte[] fileBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            String text = new PDFTextStripper().getText(document);
            return new ExtractedText(text, document.getNumberOfPages());
        }
    }

    private ExtractedText extractDocx(byte[] fileBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> appendLine(text, paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> appendLine(text, cell.getText()))
            ));
            return new ExtractedText(text.toString(), null);
        }
    }

    private ExtractedText extractPptx(byte[] fileBytes) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(fileBytes))) {
            StringBuilder text = new StringBuilder();
            for (XSLFSlide slide : slideShow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        appendLine(text, textShape.getText());
                    }
                }
            }
            return new ExtractedText(text.toString(), slideShow.getSlides().size());
        }
    }

    private void appendLine(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value.trim()).append(System.lineSeparator());
        }
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ExtractedText {
        String text;
        Integer pageCount;
    }
}
