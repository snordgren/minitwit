package com.minitwit.config;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class FreemarkerRenderer {
    private final Configuration config;

    public FreemarkerRenderer() {
        this(createDefaultConfig());
    }

    public FreemarkerRenderer(Configuration config) {
        this.config = config;
    }

    public String render(String view, Map<String, Object> model) {
        try {
            StringWriter stringWriter = new StringWriter();
            Template template = config.getTemplate(view);
            template.process(model, stringWriter);
            return stringWriter.toString();
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(e);
        }
    }

    private static Configuration createDefaultConfig() {
        Configuration config = new Configuration(new Version(2, 3, 23));
        config.setClassForTemplateLoading(FreemarkerRenderer.class, "");
        return config;
    }
}
