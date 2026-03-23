package com.wk.agent.service.rag;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    public String parseDocument(String filePath) {
        Path path = Path.of(filePath);
        String extension = getFileExtension(filePath).toLowerCase();
        
        log.info("解析文档: {} (类型: {})", filePath, extension);
        
        return switch (extension) {
            case "txt", "md", "markdown" -> parseTextFile(path);
            case "html", "htm" -> parseHtmlFile(path);
            case "json" -> parseJsonFile(path);
            case "csv" -> parseCsvFile(path);
            case "pdf" -> parsePdfFile(path);
            case "doc" -> parseDocFile(path);
            case "docx" -> parseDocxFile(path);
            case "ppt" -> parsePptFile(path);
            case "pptx" -> parsePptxFile(path);
            default -> parseTextFile(path);
        };
    }

    public String parseMultipartFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename != null ? originalFilename : "").toLowerCase();
        
        log.info("解析上传文件: {} (类型: {})", originalFilename, extension);
        
        try (InputStream inputStream = file.getInputStream()) {
            return switch (extension) {
                case "txt", "md", "markdown" -> parseTextStream(inputStream);
                case "html", "htm" -> parseHtmlStream(inputStream);
                case "json" -> parseJsonStream(inputStream);
                case "csv" -> parseCsvStream(inputStream);
                case "pdf" -> parsePdfStream(inputStream);
                case "doc" -> parseDocStream(inputStream);
                case "docx" -> parseDocxStream(inputStream);
                case "ppt" -> parsePptStream(inputStream);
                case "pptx" -> parsePptxStream(inputStream);
                default -> parseTextStream(inputStream);
            };
        } catch (IOException e) {
            log.error("解析上传文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseTextStream(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes());
        } catch (IOException e) {
            log.error("读取文本流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseHtmlStream(InputStream inputStream) {
        try {
            String content = new String(inputStream.readAllBytes());
            return extractTextFromHtml(content);
        } catch (IOException e) {
            log.error("读取HTML流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseJsonStream(InputStream inputStream) {
        try {
            String content = new String(inputStream.readAllBytes());
            return formatJsonAsText(content);
        } catch (IOException e) {
            log.error("读取JSON流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseCsvStream(InputStream inputStream) {
        try {
            String content = new String(inputStream.readAllBytes());
            return formatCsvAsText(content);
        } catch (IOException e) {
            log.error("读取CSV流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parsePdfStream(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);
                
                StringBuilder result = new StringBuilder();
                result.append("PDF文档\n");
                result.append("页数: ").append(document.getNumberOfPages()).append("\n\n");
                result.append(text);
                
                log.info("PDF解析成功: {} 页", document.getNumberOfPages());
                return result.toString();
            }
        } catch (IOException e) {
            log.error("解析PDF流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseDocStream(InputStream inputStream) {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            
            String text = extractor.getText();
            
            StringBuilder result = new StringBuilder();
            result.append("Word文档(.doc)\n\n");
            result.append(text);
            
            log.info("DOC解析成功");
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析DOC流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseDocxStream(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String text = extractor.getText();
            
            StringBuilder result = new StringBuilder();
            result.append("Word文档(.docx)\n\n");
            result.append(text);
            
            log.info("DOCX解析成功");
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析DOCX流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parsePptStream(InputStream inputStream) {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(inputStream)) {
            
            StringBuilder result = new StringBuilder();
            result.append("PowerPoint演示文稿(.ppt)\n");
            result.append("幻灯片数: ").append(slideShow.getSlides().size()).append("\n\n");
            
            int slideNum = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                result.append("=== 幻灯片 ").append(slideNum++).append(" ===\n");
                
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            result.append(text.trim()).append("\n");
                        }
                    }
                }
                result.append("\n");
            }
            
            log.info("PPT解析成功: {} 张幻灯片", slideShow.getSlides().size());
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析PPT流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parsePptxStream(InputStream inputStream) {
        try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            
            StringBuilder result = new StringBuilder();
            result.append("PowerPoint演示文稿(.pptx)\n");
            result.append("幻灯片数: ").append(slideShow.getSlides().size()).append("\n\n");
            
            int slideNum = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                result.append("=== 幻灯片 ").append(slideNum++).append(" ===\n");
                
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            result.append(text.trim()).append("\n");
                        }
                    }
                }
                result.append("\n");
            }
            
            log.info("PPTX解析成功: {} 张幻灯片", slideShow.getSlides().size());
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析PPTX流失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseTextFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            log.error("读取文本文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseHtmlFile(Path path) {
        try {
            String content = Files.readString(path);
            return extractTextFromHtml(content);
        } catch (IOException e) {
            log.error("读取HTML文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractTextFromHtml(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            doc.select("script, style, nav, footer, header, aside").remove();
            
            StringBuilder result = new StringBuilder();
            
            String title = doc.title();
            if (title != null && !title.isEmpty()) {
                result.append("标题: ").append(title).append("\n\n");
            }
            
            Element metaDesc = doc.selectFirst("meta[name=description]");
            if (metaDesc != null) {
                String desc = metaDesc.attr("content");
                if (desc != null && !desc.isEmpty()) {
                    result.append("描述: ").append(desc).append("\n\n");
                }
            }
            
            Elements articles = doc.select("article, main, .content, .article, #content");
            if (!articles.isEmpty()) {
                result.append("正文内容:\n");
                for (Element article : articles) {
                    result.append(article.text()).append("\n\n");
                }
            } else {
                Element body = doc.body();
                if (body != null) {
                    result.append(body.text());
                }
            }
            
            Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
            if (!headings.isEmpty()) {
                result.append("\n文档结构:\n");
                for (Element heading : headings) {
                    String level = heading.tagName().replace("h", "");
                    result.append("  ".repeat(Integer.parseInt(level) - 1))
                          .append(heading.text()).append("\n");
                }
            }
            
            return result.toString().trim();
            
        } catch (Exception e) {
            log.warn("JSoup解析失败，使用简单提取: {}", e.getMessage());
            return fallbackHtmlExtraction(html);
        }
    }

    private String fallbackHtmlExtraction(String html) {
        String text = html;
        text = text.replaceAll("<script[^>]*>.*?</script>", "");
        text = text.replaceAll("<style[^>]*>.*?</style>", "");
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private String parseJsonFile(Path path) {
        try {
            String content = Files.readString(path);
            return formatJsonAsText(content);
        } catch (IOException e) {
            log.error("读取JSON文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String formatJsonAsText(String json) {
        StringBuilder result = new StringBuilder();
        
        json = json.trim();
        if (json.startsWith("{")) {
            result.append("JSON对象内容:\n");
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    result.append("- ").append(key).append(": ").append(value).append("\n");
                }
            }
        } else if (json.startsWith("[")) {
            result.append("JSON数组内容:\n");
            json = json.substring(1, json.length() - 1);
            String[] items = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (int i = 0; i < items.length; i++) {
                result.append(i + 1).append(". ").append(items[i].trim().replace("\"", "")).append("\n");
            }
        }
        
        return result.toString();
    }

    private String parseCsvFile(Path path) {
        try {
            String content = Files.readString(path);
            return formatCsvAsText(content);
        } catch (IOException e) {
            log.error("读取CSV文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String formatCsvAsText(String csv) {
        StringBuilder result = new StringBuilder();
        String[] lines = csv.split("\n");
        
        if (lines.length > 0) {
            String[] headers = lines[0].split(",");
            result.append("CSV数据表:\n");
            result.append("列: ").append(String.join(", ", headers)).append("\n\n");
            
            for (int i = 1; i < lines.length && i <= 100; i++) {
                String[] values = lines[i].split(",");
                result.append("行").append(i).append(": ");
                for (int j = 0; j < headers.length && j < values.length; j++) {
                    result.append(headers[j].trim()).append("=").append(values[j].trim()).append("; ");
                }
                result.append("\n");
            }
        }
        
        return result.toString();
    }

    private String parsePdfFile(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            
            StringBuilder result = new StringBuilder();
            result.append("PDF文档: ").append(path.getFileName()).append("\n");
            result.append("页数: ").append(document.getNumberOfPages()).append("\n\n");
            result.append(text);
            
            log.info("PDF解析成功: {} 页", document.getNumberOfPages());
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析PDF文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseDocFile(Path path) {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(path));
             WordExtractor extractor = new WordExtractor(document)) {
            
            String text = extractor.getText();
            
            StringBuilder result = new StringBuilder();
            result.append("Word文档(.doc): ").append(path.getFileName()).append("\n\n");
            result.append(text);
            
            log.info("DOC解析成功");
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析DOC文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseDocxFile(Path path) {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String text = extractor.getText();
            
            StringBuilder result = new StringBuilder();
            result.append("Word文档(.docx): ").append(path.getFileName()).append("\n\n");
            result.append(text);
            
            log.info("DOCX解析成功");
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析DOCX文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parsePptFile(Path path) {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(Files.newInputStream(path))) {
            
            StringBuilder result = new StringBuilder();
            result.append("PowerPoint演示文稿(.ppt): ").append(path.getFileName()).append("\n");
            result.append("幻灯片数: ").append(slideShow.getSlides().size()).append("\n\n");
            
            int slideNum = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                result.append("=== 幻灯片 ").append(slideNum++).append(" ===\n");
                
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            result.append(text.trim()).append("\n");
                        }
                    }
                }
                result.append("\n");
            }
            
            log.info("PPT解析成功: {} 张幻灯片", slideShow.getSlides().size());
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析PPT文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String parsePptxFile(Path path) {
        try (XMLSlideShow slideShow = new XMLSlideShow(Files.newInputStream(path))) {
            
            StringBuilder result = new StringBuilder();
            result.append("PowerPoint演示文稿(.pptx): ").append(path.getFileName()).append("\n");
            result.append("幻灯片数: ").append(slideShow.getSlides().size()).append("\n\n");
            
            int slideNum = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                result.append("=== 幻灯片 ").append(slideNum++).append(" ===\n");
                
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            result.append(text.trim()).append("\n");
                        }
                    }
                }
                result.append("\n");
            }
            
            log.info("PPTX解析成功: {} 张幻灯片", slideShow.getSlides().size());
            return result.toString();
            
        } catch (IOException e) {
            log.error("解析PPTX文件失败: {}", e.getMessage());
            return null;
        }
    }

    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1);
        }
        return "";
    }

    public String getContentType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        return switch (extension) {
            case "txt" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "html", "htm" -> "text/html";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
