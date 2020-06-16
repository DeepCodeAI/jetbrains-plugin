package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.*;
import org.jetbrains.annotations.NotNull;

public final class AnalysisData extends AnalysisDataBase {

  private static final AnalysisData INSTANCE =
      new AnalysisData(
          PDU.getInstance(),
          HashContentUtils.getInstance(),
          DeepCodeParams.getInstance(),
          DCLogger.getInstance());

  public static AnalysisData getInstance() {
    return INSTANCE;
  }

  private AnalysisData(
      @NotNull PlatformDependentUtilsBase platformDependentUtils,
      @NotNull HashContentUtilsBase hashContentUtils,
      @NotNull DeepCodeParamsBase deepCodeParams,
      @NotNull DCLoggerBase dcLogger) {
    super(platformDependentUtils, hashContentUtils, deepCodeParams, dcLogger);
  }
}
