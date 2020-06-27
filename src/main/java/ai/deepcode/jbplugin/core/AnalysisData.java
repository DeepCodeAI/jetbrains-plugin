package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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

  @Override
  protected void updateUIonFilesRemovalFromCache(@NotNull Collection<Object> files) {
    // code from T0D0 already have listener for updates
  }
}
