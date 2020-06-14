package ai.deepcode.javaclient.core;

import java.util.List;

public class SuggestionForFile {
  private final String id;
  private final String message;
  private final int severity;
  private final List<MyTextRange> ranges;

  public SuggestionForFile(String id, String message, int severity, List<MyTextRange> ranges) {
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

  public List<MyTextRange> getRanges() {
    return ranges;
  }

  public int getSeverity() {
    return severity;
  }
}
