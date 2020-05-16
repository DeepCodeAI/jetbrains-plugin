package ai.deepcode.jbplugin.core;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class DCLogger {
  private DCLogger() {}

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode");
  private static final SimpleDateFormat HMSS = new SimpleDateFormat("H:mm:ss,S");
  private static final SimpleDateFormat mmssSSS = new SimpleDateFormat("mm:ss,SSS");

  public static synchronized void info(String message) {
    if (!LOG.isInfoEnabled()) return;
    doLogging(message, LOG::info);
  }

  public static synchronized void warn(String message) {
    if (!LOG.isWarnEnabled()) return;
    doLogging(message, LOG::warn);
  }

  private static synchronized void doLogging(String message, Consumer<String> logFunction) {
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

    String currentThread = " [" + Thread.currentThread().getName() + "] ";

    StringJoiner joiner = new StringJoiner(" -> ");
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (int i = stackTrace.length - 1; i > 2; i--) {
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
    String rwAccess = (application.isReadAccessAllowed() ? "R" : "-");
    rwAccess += (application.isWriteAccessAllowed() ? "W" : "-");

    // fixme presume we work with one project only
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    String mode =
        (openProjects.length != 0)
            ? DumbService.getInstance(openProjects[0]).isDumb() ? "D" : "S"
            : "X";

    final ProgressIndicator currentProgressIndicator =
        ProgressManager.getInstance().getProgressIndicator();
    String progressIndicator =
        (currentProgressIndicator == null)
            ? ""
            : "\nProgressIndicator [" + currentProgressIndicator.toString() + "]";

    final String[] lines = message.split("[\n\r]");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      line = (i == 0 ? currentTime : "            ") + line;
      if (i == lines.length - 1) {
        line += "\n" + rwAccess + mode + currentThread + myClassesStackTrace;
        line += progressIndicator;
      }

      logFunction.accept(line);
    }
  }
}
