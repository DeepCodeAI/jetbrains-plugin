package ai.deepcode.jbplugin.core;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BulkMode {
  private static final Map<Project, Integer> mapProject2RequestsCounter = new ConcurrentHashMap<>();

  private static int getBulkRequestsCount(@NotNull Project project) {
    return mapProject2RequestsCounter.computeIfAbsent(project, p -> 0);
  }

  /** No events for individual files should be processed */
  public static boolean isActive(@NotNull Project project) {
    return getBulkRequestsCount(project) > 0;
  }

  public static void set(@NotNull Project project) {
    final int counter = getBulkRequestsCount(project) + 1;
    if (counter == 1) {
      // cancel all running tasks first
      RunUtils.cancelRunningIndicators(project);
    }
    DCLogger.getInstance().logInfo("BulkMode ON with " + counter + " total requests");
    mapProject2RequestsCounter.put(project, counter);
  }

  public static void unset(@NotNull Project project) {
    final int counter = getBulkRequestsCount(project) - 1;
    if (counter >= 0) {
      mapProject2RequestsCounter.put(project, counter);
      DCLogger.getInstance().logInfo("BulkMode OFF with " + counter + " total requests");
    } else {
      DCLogger.getInstance().logWarn("BulkMode OFF request with already " + counter + " total requests");
    }
  }

  public static void forceUnset(@NotNull Project project) {
    mapProject2RequestsCounter.put(project, 0);
    DCLogger.getInstance().logInfo("BulkMode OFF forced");
  }
}
