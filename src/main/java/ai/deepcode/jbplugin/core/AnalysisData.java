package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.*;

public final class AnalysisData extends AnalysisDataBase {

  private static final AnalysisData INSTANCE = new AnalysisData();

  public static AnalysisData getInstance() {
    return INSTANCE;
  }

  private AnalysisData() {
    super(
        PDU.getInstance(),
        HashContentUtils.getInstance(),
        DeepCodeParams.getInstance(),
            DCLogger.getInstance());
  }
}
