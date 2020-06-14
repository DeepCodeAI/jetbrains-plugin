package ai.deepcode.javaclient.core;

public class MyTextRange {
  private final int start;
  private final int end;

  public MyTextRange(int start, int end) {

    this.start = start;
    this.end = end;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }
}
