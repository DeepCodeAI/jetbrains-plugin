package ai.deepcode.jbplugin.core;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class DCLogger {
  private DCLogger() {}

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode");
  private static final SimpleDateFormat HMSS = new SimpleDateFormat("H:mm:ss,S");
  private static final SimpleDateFormat mmssSSS = new SimpleDateFormat("mm:ss,SSS");

  public static synchronized void info(String message) {
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
    // todo: made DeepCode console

    String currentThread = "[" + Thread.currentThread().getName() + "] ";

    StringJoiner joiner = new StringJoiner(" -> ");
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (int i = stackTrace.length - 1; i > 1; i--) {
      StackTraceElement ste = stackTrace[i];
      if (ste.getClassName().contains("ai.deepcode.jbplugin")) {
        String s =
            ste.getClassName().substring(ste.getClassName().lastIndexOf('.') + 1)
                + "."
                + ste.getMethodName();
        joiner.add(s);
      }
    }
    String myClassesStackTrace = joiner.toString();

    final Application application = ApplicationManager.getApplication();
    String rwAccess = (application.isReadAccessAllowed() ? "R" : " ");
    rwAccess += (application.isWriteAccessAllowed() ? "W" : " ");
    rwAccess += " ";

    final String[] lines = message.split("[\n\r]");
    LOG.info(currentTime + rwAccess + currentThread + myClassesStackTrace);
    for (String line : lines) {
      LOG.info("    " + line);
    }
  }
}
