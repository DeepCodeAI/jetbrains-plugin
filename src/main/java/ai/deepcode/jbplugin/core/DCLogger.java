package ai.deepcode.jbplugin.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

public class DCLogger {
  private DCLogger() {}

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode");
  private static final SimpleDateFormat HMSS = new SimpleDateFormat("H:mm:ss,S");
  private static final SimpleDateFormat mmssSSS = new SimpleDateFormat("mm:ss,SSS");

  public static void info(String message) {
    if (!LOG.isInfoEnabled()) return;
    //    String currentTime = "[" + HMSS.format(System.currentTimeMillis()) + "] ";
    String currentTime = "[" + mmssSSS.format(System.currentTimeMillis()) + "] ";
    if (message.length() > 500) {
      message =
          message.substring(0, 500)
              + " ... ["
              + (message.length() - 500)
              + " more symbols were cut]";
    }
    // fixme: made DeepCode console

    //    System.out.println(currentTime + message);
    boolean firstLine = true;
    for (String line : message.split("[\n\r]")) {
      if (firstLine) {
        line = currentTime + line;
        firstLine = false;
      }
      LOG.info(line);
    }
  }
}
