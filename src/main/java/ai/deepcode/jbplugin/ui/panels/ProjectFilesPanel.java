package ai.deepcode.jbplugin.ui.panels;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.AutoScrollToSourceHandler;

public class ProjectFilesPanel extends SimpleToolWindowPanel {

  public ProjectFilesPanel(boolean vertical) {
    super(vertical);
    initUI();
  }

  private void initUI() {

    // todo Popup menu
    DefaultActionGroup group = new DefaultActionGroup();

    // Create tool bars and register custom shortcuts
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
/*
    toolbarGroup.add(new PreviousOccurenceToolbarAction(myOccurenceNavigator));
    toolbarGroup.add(new NextOccurenceToolbarAction(myOccurenceNavigator));
    toolbarGroup.add(new TodoPanel.MyPreviewAction());
*/
    toolbarGroup.add(new MyAutoScrollToSourceHandler().createToggleAction());

    setToolbar(ActionManager.getInstance().createActionToolbar("DeepCodeToolbar", toolbarGroup, false).getComponent());

  }


  /**
   * Provides support for "auto scroll to source" functionality
   */
  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    MyAutoScrollToSourceHandler() {}

    @Override
    protected boolean isAutoScrollMode() {
      return true;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {}
  }

}
