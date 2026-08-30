package com.icbc.financialinfo.modules.report.properties;

import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "app.report")
public class ReportProperties {

  @Setter
  private String outputDir = "target/generated-reports";

  @Setter
  private String newsTable = "news_pool";

  @Setter
  private String reportTable = "generated_report";

  @Setter
  private String reportArticleTable = "report_article";

  private final Dify dify = new Dify();

  public Path resolveOutputDirectory() {
    Path path = Path.of(outputDir);
    if (path.isAbsolute()) {
      return path;
    }
    return Path.of("").toAbsolutePath().resolve(path).normalize();
  }

  @Setter
  @Getter
  public static class Dify {

    private String baseUrl;
    private String apiKey;
    private String endpoint = "/v1/workflows/run";
    private String responseMode = "blocking";
    private String user = "report-module";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 600;
    private boolean mockEnabled = false;
  }
}
