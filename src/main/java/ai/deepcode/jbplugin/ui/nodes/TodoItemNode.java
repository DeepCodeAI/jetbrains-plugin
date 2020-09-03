// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.jbplugin.ui.HighlightedRegionProvider;
import ai.deepcode.jbplugin.ui.SmartTodoItemPointer;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.TodoItem;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TodoItemNode extends BaseToDoNode<SmartTodoItemPointer>
    implements HighlightedRegionProvider {
  private static final Logger LOG = Logger.getInstance(TodoItem.class);

  private final List<HighlightedRegion> myHighlightedRegions;
  private final List<HighlightedRegionProvider> myAdditionalLines;

  public TodoItemNode(
      Project project, @NotNull SmartTodoItemPointer value, TodoTreeBuilder builder) {
    super(project, value, builder);
    RangeMarker rangeMarker = value.getRangeMarker();
    LOG.assertTrue(rangeMarker.isValid());

    myHighlightedRegions = ContainerUtil.createConcurrentList();
    myAdditionalLines = ContainerUtil.createConcurrentList();
  }

  @Override
  public boolean contains(Object element) {
    return canRepresent(element);
  }

  @Override
  public boolean canRepresent(Object element) {
    SmartTodoItemPointer value = getValue();
    TodoItem item = value != null ? value.getTodoItem() : null;
    return Comparing.equal(item, element);
  }

  @Override
  public int getFileCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public List<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    SmartTodoItemPointer todoItemPointer = getValue();
    assert todoItemPointer != null;
    TodoItem todoItem = todoItemPointer.getTodoItem();
    RangeMarker myRangeMarker = todoItemPointer.getRangeMarker();
    if (!todoItem.getFile().isValid()
        || !myRangeMarker.isValid()
        || myRangeMarker.getStartOffset() == myRangeMarker.getEndOffset()) {
      myRangeMarker.dispose();
      setValue(null);
      return;
    }

    myHighlightedRegions.clear();
    myAdditionalLines.clear();

    Document document = todoItemPointer.getDocument();
    CharSequence chars = document.getCharsSequence();
    int startOffset = myRangeMarker.getStartOffset();
    int endOffset = myRangeMarker.getEndOffset();
    int lineNumber = document.getLineNumber(startOffset);
    int lineStartOffset = document.getLineStartOffset(lineNumber);
    int columnNumber = startOffset - lineStartOffset;

    // skip all white space characters

    while (lineStartOffset < document.getTextLength()
        && (chars.charAt(lineStartOffset) == '\t' || chars.charAt(lineStartOffset) == ' ')) {
      lineStartOffset++;
    }
    int lineEndOffset = document.getLineEndOffset(lineNumber);

    // Update highlighted regions
    myHighlightedRegions.clear();

    final String lineColumnPrefix = "(" + (lineNumber + 1) + ", " + (columnNumber + 1) + ") ";
    String fullPrefix = lineColumnPrefix;
    if (todoItem instanceof MarkerItemImpl) {
      final String markerPrefixSpacer = "      ";
      final String markerMsg = ((MarkerItemImpl) todoItem).getMessage();
      final String markerMsgPostfix = " :    ";

      fullPrefix = markerPrefixSpacer + lineColumnPrefix + markerMsg + markerMsgPostfix;

      TextAttributes markerMsgAttributes =
          EditorColorsManager.getInstance()
              .getGlobalScheme()
              .getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER)
              .clone();
      markerMsgAttributes.setFontType(Font.ITALIC);
      myHighlightedRegions.add(
          new HighlightedRegion(
                  markerPrefixSpacer.length() + lineColumnPrefix.length(),
                  markerPrefixSpacer.length() + lineColumnPrefix.length() + markerMsg.length(),
                  markerMsgAttributes));
    }

    EditorHighlighter highlighter = myBuilder.getHighlighter(todoItem.getFile(), document);
    collectHighlights(
        myHighlightedRegions,
        highlighter,
        lineStartOffset,
        lineEndOffset,
        fullPrefix.length());

    TextAttributes attributes =
        EditorColorsManager.getInstance()
            .getGlobalScheme()
            .getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES)
            .clone();
    // todoItem.getPattern().getAttributes().getTextAttributes();
    myHighlightedRegions.add(
        new HighlightedRegion(
            fullPrefix.length() + startOffset - lineStartOffset,
            fullPrefix.length() + endOffset - lineStartOffset,
            attributes));

    // Update name
    String highlightedText =
        chars.subSequence(lineStartOffset, Math.min(lineEndOffset, chars.length())).toString();
    presentation.setPresentableText(fullPrefix + highlightedText);

    // Update icon
    if (!(todoItem instanceof MarkerItemImpl)) {
      Icon newIcon = todoItem.getPattern().getAttributes().getIcon();
      presentation.setIcon(newIcon);
    }

/*
    for (RangeMarker additionalMarker : todoItemPointer.getAdditionalRangeMarkers()) {
      if (!additionalMarker.isValid()) break;
      ArrayList<HighlightedRegion> highlights = new ArrayList<>();
      int lineNum = document.getLineNumber(additionalMarker.getStartOffset());
      int lineStart = document.getLineStartOffset(lineNum);
      int lineEnd = document.getLineEndOffset(lineNum);
      int lineStartNonWs = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (lineStartNonWs > additionalMarker.getStartOffset()
          || lineEnd < additionalMarker.getEndOffset()) {
        // can happen for an invalid (obsolete) node, tree implementation can call this method for
        // such a node
        break;
      }
      // syntax highlighter
      collectHighlights(highlights, highlighter, lineStartNonWs, lineEnd, linePrefix.length());
      // underscore marked place
      highlights.add(
          new HighlightedRegion(
              linePrefix.length() + additionalMarker.getStartOffset() - lineStartNonWs,
              linePrefix.length() + additionalMarker.getEndOffset() - lineStartNonWs,
              attributes));
      final String markerText =
          linePrefix + document.getText(new TextRange(lineStartNonWs, lineEnd));
      myAdditionalLines.add(new AdditionalTodoLine(markerText, highlights));
    }
*/
  }

  private static void collectHighlights(
      @NotNull List<? super HighlightedRegion> highlights,
      @NotNull EditorHighlighter highlighter,
      int startOffset,
      int endOffset,
      int highlightOffsetShift) {
    HighlighterIterator iterator = highlighter.createIterator(startOffset);
    while (!iterator.atEnd()) {
      int start = Math.max(iterator.getStart(), startOffset);
      int end = Math.min(iterator.getEnd(), endOffset);
      if (start >= endOffset) break;

      TextAttributes attributes = iterator.getTextAttributes();
      int fontType = attributes.getFontType();
      if ((fontType & Font.BOLD) != 0) { // suppress bold attribute
        attributes = attributes.clone();
        attributes.setFontType(fontType & ~Font.BOLD);
      }
      HighlightedRegion region =
          new HighlightedRegion(
              highlightOffsetShift + start - startOffset,
              highlightOffsetShift + end - startOffset,
              attributes);
      highlights.add(region);
      iterator.advance();
    }
  }

  public int getRowCount() {
    return myAdditionalLines.size() + 1;
  }

  /*
    @Override
    public String getTestPresentation() {
      return "Item: " + getValue().getTodoItem().getTextRange();
    }
  */

  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "Item: " + getValue().getTodoItem().getTextRange();
  }

  @Override
  public int getWeight() {
    return 5;
  }

  @NotNull
  public List<HighlightedRegionProvider> getAdditionalLines() {
    return myAdditionalLines;
  }

  private static class AdditionalTodoLine implements HighlightedRegionProvider {
    private final String myText;
    private final List<HighlightedRegion> myHighlights;

    private AdditionalTodoLine(String text, List<HighlightedRegion> highlights) {
      myText = text;
      myHighlights = highlights;
    }

    @Override
    public Iterable<HighlightedRegion> getHighlightedRegions() {
      return myHighlights;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
