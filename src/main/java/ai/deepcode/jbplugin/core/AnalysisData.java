package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.AnalysisDataBase;
import ai.deepcode.javaclient.core.DeepCodeParamsBase;
import ai.deepcode.javaclient.core.HashContentUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import org.jetbrains.annotations.NotNull;

public final class AnalysisData extends AnalysisDataBase {

  private static final AnalysisData INSTANCE =
      new AnalysisData(
          PDU.getInstance(),
          HashContentUtils.getInstance(),
          DeepCodeParams.getInstance());

  public static AnalysisData getInstance() {
    return INSTANCE;
  }

  protected AnalysisData(
      @NotNull PlatformDependentUtilsBase platformDependentUtils,
      @NotNull HashContentUtilsBase hashContentUtils,
      @NotNull DeepCodeParamsBase deepCodeParams) {
    super(platformDependentUtils, hashContentUtils, deepCodeParams);
  }
}
