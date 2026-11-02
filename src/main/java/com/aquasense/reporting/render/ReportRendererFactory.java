package com.aquasense.reporting.render;

import com.aquasense.reporting.domain.ReportFormat;
import org.springframework.stereotype.Component;

@Component
public class ReportRendererFactory {

    public ReportRenderer forFormat(ReportFormat format) {
        return switch (format) {
            case MARKDOWN -> new MarkdownRenderer();
            case HTML -> new HtmlRenderer();
        };
    }
}
