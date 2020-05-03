package ai.deepcode.jbplugin.core;

import com.intellij.openapi.util.TextRange;

import java.util.List;

public class SuggestionForFile {
  private final String id;
  private final String message;
  private final int severity;
  private final List<TextRange> ranges;

  public SuggestionForFile(String id, String message, int severity, List<TextRange> ranges) {
    this.id = id;
    this.message = message;
    this.severity = severity;
    this.ranges = ranges;
  }

  public String getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }

  public List<TextRange> getRanges() {
    return ranges;
  }

  public int getSeverity() {
    return severity;
  }
}
