package com.icbc.financialinfo.modules.report.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DifyWorkflowRequest(List<DifyNewsArticleInput> articles) {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public DifyWorkflowRequest {
    articles = articles == null ? List.of() : List.copyOf(articles);
  }

  public Map<String, Object> toInputs() {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("news_list", serializeArticles());
    return inputs;
  }

  private String serializeArticles() {
    try {
      return OBJECT_MAPPER.writeValueAsString(articles);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize Dify news_list input", ex);
    }
  }
}
