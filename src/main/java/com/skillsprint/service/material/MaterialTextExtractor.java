package com.skillsprint.service.material;

import com.skillsprint.enums.material.FileType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class MaterialTextExtractor {

    private static final Pattern DOCX_HEADING_STYLE_PATTERN =
            Pattern.compile("(?i).*heading\\s*([1-5]).*");

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
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement.getElementType() == BodyElementType.PARAGRAPH
                        && bodyElement instanceof XWPFParagraph paragraph) {
                    appendParagraph(text, paragraph);
                }
                if (bodyElement.getElementType() == BodyElementType.TABLE
                        && bodyElement instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
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

    private void appendParagraph(StringBuilder builder, XWPFParagraph paragraph) {
        String value = paragraph.getText();
        if (value == null || value.isBlank()) {
            return;
        }

        int headingLevel = resolveHeadingLevel(paragraph);
        if (headingLevel > 0) {
            appendLine(builder, "#".repeat(headingLevel) + " " + value);
            return;
        }

        appendLine(builder, value);
    }

    private void appendTable(StringBuilder builder, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            String rowText = row.getTableCells().stream()
                    .map(cell -> cell.getText() == null ? "" : cell.getText().trim())
                    .filter(value -> !value.isBlank())
                    .reduce((left, right) -> left + " | " + right)
                    .orElse("");
            appendLine(builder, rowText);
        }
    }

    private int resolveHeadingLevel(XWPFParagraph paragraph) {
        int styleLevel = resolveHeadingLevel(paragraph.getStyle());
        if (styleLevel > 0) {
            return styleLevel;
        }
        return resolveHeadingLevel(paragraph.getStyleID());
    }

    private int resolveHeadingLevel(String style) {
        if (style == null || style.isBlank()) {
            return 0;
        }

        Matcher matcher = DOCX_HEADING_STYLE_PATTERN.matcher(style);
        if (!matcher.matches()) {
            return 0;
        }

        return Integer.parseInt(matcher.group(1));
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ExtractedText {
        String text;
        Integer pageCount;
    }
}
