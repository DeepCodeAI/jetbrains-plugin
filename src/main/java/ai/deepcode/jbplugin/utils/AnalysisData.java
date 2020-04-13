package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.responses.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AnalysisData {
  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalysisData");
  private static final Map<PsiFile, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();

  private AnalysisData() {}

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<PsiFile, List<SuggestionForFile>> mapFile2Suggestions =
      new ConcurrentHashMap<>();

  public static class SuggestionForFile {
    private String id;
    private String message;
    private int severity;
    private List<TextRange> ranges;

    public SuggestionForFile(String id, String message, int severity, List<TextRange> ranges) {
      this.id = id;
      this.message = message;
      this.severity = severity;
      this.ranges = ranges;
    }

    public String getId() {
      return id;
    }

    public String getMessage() {
      return message;
    }

    public List<TextRange> getRanges() {
      return ranges;
    }

    public int getSeverity() {
      return severity;
    }
  }

  private static Set<Project> projects = ConcurrentHashMap.newKeySet();

  /** Add File Listener to clear caches for file if it was changed. */
  private static void addFileListener(@NotNull final Project project) {
    if (!projects.contains(project)) {
      PsiManager.getInstance(project)
          .addPsiTreeChangeListener(
              new PsiTreeChangeAdapter() {
                @Override
                public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
                  PsiFile file = event.getFile();
                  if (file != null && mapFile2Suggestions.remove(file) != null) {

                    // fixme: debug only
                    System.out.println("Removed from cache: " + file);
                  }
                }
              });
      projects.add(project);
    }
  }

  /** see {@link #getAnalysis(java.util.Collection)} */
  @NotNull
  public static List<SuggestionForFile> getAnalysis(@NotNull PsiFile psiFile) {
    return getAnalysis(Collections.singleton(psiFile))
        .getOrDefault(psiFile, Collections.emptyList());
  }

  /**
   * Return Suggestions mapped to Files.
   *
   * <p>Look into cached results first and if not found retrieve analysis results from server.
   *
   * @param psiFiles
   * @return
   */
  @NotNull
  public static Map<PsiFile, List<SuggestionForFile>> getAnalysis(
      @NotNull Collection<PsiFile> psiFiles) {

    // fixme: debug only
    System.out.println(
        "--------------\n"
            + "Analysis requested for files: "
            + psiFiles
            + " at "
            + new SimpleDateFormat("m:s,S").format(System.currentTimeMillis()));

    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    psiFiles.stream().map(PsiElement::getProject).distinct().forEach(AnalysisData::addFileListener);
    mapFile2Suggestions.putAll(
        retrieveSuggestions(
            psiFiles.stream()
                .filter(file -> !mapFile2Suggestions.containsKey(file))
                .collect(Collectors.toSet())));
    for (PsiFile psiFile : psiFiles) {
      List<SuggestionForFile> suggestions = mapFile2Suggestions.get(psiFile);
      if (suggestions != null) {
        result.put(psiFile, suggestions);
      } else {
        LOG.error("Suggestions not found for file: {}", psiFile);
      }
    }
    return result;
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> retrieveSuggestions(
      @NotNull Set<PsiFile> psiFiles) {
    if (psiFiles.isEmpty()) return Collections.emptyMap();
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    long bundleSize = 0;
    List<PsiFile> filesChunk = new ArrayList<>();
    for (PsiFile psiFile : psiFiles) {
      final long fileSize = psiFile.getVirtualFile().getLength();
      if (bundleSize + fileSize > MAX_BUNDLE_SIZE) {
        // fixme: debug only
        System.out.println("Bundle size: " + bundleSize);
        result.putAll(doRetrieveSuggestions(filesChunk));
        bundleSize = 0;
        filesChunk.clear();
      }
      bundleSize += fileSize;
      filesChunk.add(psiFile);
    }
    // fixme: debug only
    System.out.println("Bundle size: " + bundleSize);
    result.putAll(doRetrieveSuggestions(filesChunk));
    return result;
  }

  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> doRetrieveSuggestions(
      @NotNull Collection<PsiFile> psiFiles) {
    ProgressIndicator progress = new StatusBarProgress();
//    progress.setIndeterminate(false);
    progress.start();

    ProgressManager.checkCanceled();
    progress.setText("Preparing files for upload...");
    //fixme if not logged?
    String loggedToken = DeepCodeParams.getSessionToken();
    FileContentRequest files =
        new FileContentRequest(
            psiFiles.stream().map(AnalysisData::createFileContent).collect(Collectors.toList()));

    ProgressManager.checkCanceled();
    progress.setText("Uploading files to the server...");
    CreateBundleResponse createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);

    ProgressManager.checkCanceled();
    progress.setText("Waiting for analysis from server...");
    GetAnalysisResponse response;
    int counter = 0;
    do {
//      progress.setFraction(((double) counter) / 10);
      response = DeepCodeRestApi.getAnalysis(loggedToken, createBundleResponse.getBundleId());

      // todo: show progress notification
      // fixme: debug only
      System.out.println("    " + response);

      ProgressManager.checkCanceled();
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
        Thread.currentThread().interrupt();
      }
      // fixme
      if (counter == 10) break;
      counter++;
    } while (!response.getStatus().equals("DONE"));
    progress.stop();

    return parseGetAnalysisResponse(psiFiles, response);
  }

  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> parseGetAnalysisResponse(
      @NotNull Collection<PsiFile> psiFiles, GetAnalysisResponse response) {
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    if (!response.getStatus().equals("DONE")) return EMPTY_MAP;
    AnalysisResults analysisResults = response.getAnalysisResults();
    if (analysisResults == null) {
      LOG.error("AnalysisResults is null for: {}", response);
      return EMPTY_MAP;
    }
    for (PsiFile psiFile : psiFiles) {
      FileSuggestions fileSuggestions =
          analysisResults.getFiles().get("/" + psiFile.getVirtualFile().getPath());
      if (fileSuggestions == null) {
        result.put(psiFile, Collections.emptyList());
        continue;
      }
      final Suggestions suggestions = analysisResults.getSuggestions();
      if (suggestions == null) {
        LOG.error("Suggestions is empty for: {}", response);
        return EMPTY_MAP;
      }
      Document document = psiFile.getViewProvider().getDocument();
      if (document == null) {
        LOG.error("Document not found for file: {}  GetAnalysisResponse: {}", psiFile, response);
        return EMPTY_MAP;
      }

      final List<SuggestionForFile> mySuggestions = new ArrayList<>();
      for (String suggestionIndex : fileSuggestions.keySet()) {
        final Suggestion suggestion = suggestions.get(suggestionIndex);
        if (suggestion == null) {
          LOG.error(
              "Suggestion not found for suggestionIndex: {}  GetAnalysisResponse: {}",
              suggestionIndex,
              response);
          return EMPTY_MAP;
        }
        final List<TextRange> ranges = new ArrayList<>();
        for (FileRange fileRange : fileSuggestions.get(suggestionIndex)) {
          final int startRow = fileRange.getRows().get(0);
          final int endRow = fileRange.getRows().get(1);
          final int startCol = fileRange.getCols().get(0) - 1; // inclusive
          final int endCol = fileRange.getCols().get(1);
          final int lineStartOffset = document.getLineStartOffset(startRow - 1); // to 0-based
          final int lineEndOffset = document.getLineStartOffset(endRow - 1);
          ranges.add(new TextRange(lineStartOffset + startCol, lineEndOffset + endCol));
        }
        mySuggestions.add(
            new SuggestionForFile(
                suggestion.getId(), suggestion.getMessage(), suggestion.getSeverity(), ranges));
      }
      result.put(psiFile, mySuggestions);
    }
    return result;
  }

  private static FileContent createFileContent(PsiFile psiFile) {
    return new FileContent("/" + psiFile.getVirtualFile().getPath(), psiFile.getText());
  }

  public static Set<PsiFile> getAllFilesWithSuggestions(@NotNull final Project project) {
    return mapFile2Suggestions.entrySet().stream()
        .filter(e -> e.getKey().getProject().equals(project))
        .filter(e -> !e.getValue().isEmpty())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public static void clearCache(@NotNull final Project project) {
    mapFile2Suggestions.keySet().stream()
            .filter(file -> file.getProject().equals(project))
            .forEach(mapFile2Suggestions::remove);
  }
}
