package com.icbc.financialinfo.modules.report.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DifyWorkflowRequest(
        String newsDate,
        String title,
        String content
) {

    public Map<String, Object> toInputs() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("news_date", newsDate);
        inputs.put("title", title);
        inputs.put("content", content);
        return inputs;
    }
}
