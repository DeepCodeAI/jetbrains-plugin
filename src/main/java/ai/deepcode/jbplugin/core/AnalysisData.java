package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.ExtendBundleRequest;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileHash2ContentRequest;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.responses.*;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ai.deepcode.jbplugin.core.DCLogger.info;
import static ai.deepcode.jbplugin.core.DCLogger.warn;

public final class AnalysisData {

  private static final String UPLOADING_FILES_TEXT = "DeepCode: Uploading files to the server... ";
  private static final String PREPARE_FILES_TEXT = "DeepCode: Preparing files for upload... ";
  private static final String WAITING_FOR_ANALYSIS_TEXT =
      "DeepCode: Waiting for analysis from server... ";

  private AnalysisData() {}

  //  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalysisData");
  private static final Map<PsiFile, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();
  private static Map<Project, String> mapProject2analysisUrl = new ConcurrentHashMap<>();

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<PsiFile, List<SuggestionForFile>> mapFile2Suggestions =
      new ConcurrentHashMap<>();

  private static final Map<Project, String> mapProject2BundleId = new ConcurrentHashMap<>();

  // Mutex need to be requested to change mapFile2Suggestions
  private static final ReentrantLock MUTEX = new ReentrantLock();

  /** see getAnalysis() below} */
  @NotNull
  public static List<SuggestionForFile> getAnalysis(@NotNull PsiFile psiFile) {
    return getAnalysis(Collections.singleton(psiFile))
        .getOrDefault(psiFile, Collections.emptyList());
  }

  /**
   * Return Suggestions mapped to Files.
   *
   * <p>Look into cached results ONLY.
   *
   * @param psiFiles
   * @return
   */
  @NotNull
  public static Map<PsiFile, List<SuggestionForFile>> getAnalysis(
      @NotNull Collection<PsiFile> psiFiles) {
    if (psiFiles.isEmpty()) {
      warn("getAnalysis requested for empty list of files");
      return Collections.emptyMap();
    }
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    final Collection<PsiFile> brokenKeys = new ArrayList<>();
    for (PsiFile psiFile : psiFiles) {
      List<SuggestionForFile> suggestions = mapFile2Suggestions.get(psiFile);
      if (suggestions != null) {
        result.put(psiFile, suggestions);
      } else {
        brokenKeys.add(psiFile);
      }
    }
    if (!brokenKeys.isEmpty()) {
      warn("Suggestions not found for " + brokenKeys.size() + " files: " + brokenKeys.toString());
    }
    return result;
  }

  public static String getAnalysisUrl(@NotNull Project project) {
    return mapProject2analysisUrl.computeIfAbsent(project, p -> "");
  }

  static boolean addProjectToCache(@NotNull Project project) {
    return mapProject2BundleId.putIfAbsent(project, "") == null;
  }

  static Set<Project> getAllCachedProject() {
    return mapProject2BundleId.keySet();
  }

  static void removeFilesFromCache(@NotNull Collection<PsiFile> files) {
    try {
      info("Request to remove from cache " + files.size() + " files: " + files);
      // todo: do we really need mutex here?
      MUTEX.lock();
      info("MUTEX LOCK");
      int removeCounter = 0;
      for (PsiFile file : files) {
        if (file != null && isFileInCache(file)) {
          mapFile2Suggestions.remove(file);
          HashContentUtils.removeHashContent(file);
          removeCounter++;
        }
      }
      info(
          "Actually removed from cache: "
              + removeCounter
              + " files. Were not in cache: "
              + (files.size() - removeCounter));
    } finally {
      info("MUTEX RELEASED");
      MUTEX.unlock();
    }
  }

  static void removeProjectFromCaches(@NotNull Project project) {
    info("Caches clearance requested for project: " + project);
    HashContentUtils.removeHashContent(project);
    if (mapProject2BundleId.remove(project) != null) {
      info("Removed from cache: " + project);
    }
    removeFilesFromCache(cachedFilesOfProject(project));
  }

  private static Collection<PsiFile> cachedFilesOfProject(@NotNull Project project) {
    return mapFile2Suggestions.keySet().stream()
        .filter(file -> file.getProject().equals(project))
        .collect(Collectors.toList());
  }

  private static boolean updateInProgress = true;

  public static boolean isUpdateAnalysisInProgress() {
    return updateInProgress;
  }

  public static void waitForUpdateAnalysisFinish() {
    while (updateInProgress) {
      RunUtils.delay(100);
    }
  }

  /*
    public static void updateCachedResultsForFile(@NotNull PsiFile psiFile) {
      updateCachedResultsForFiles(Collections.singleton(psiFile), Collections.emptyList());
    }
  */

  public static void updateCachedResultsForFiles(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull Collection<PsiFile> filesToRemove) {
    if (psiFiles.isEmpty() && filesToRemove.isEmpty()) {
      warn("updateCachedResultsForFiles requested for empty list of files");
      return;
    }
    info("Update requested for " + psiFiles.size() + " files: " + psiFiles.toString());
    Collection<PsiFile> filesToProceed = null;
    try {
      MUTEX.lock();
      info("MUTEX LOCK");
      updateInProgress = true;
      filesToProceed =
          // DeepCodeUtils.computeNonBlockingReadAction(
          // () ->
          psiFiles.stream()
              .filter(Objects::nonNull)
              // .filter(PsiFile::isValid)
              .filter(file -> !mapFile2Suggestions.containsKey(file))
              .collect(Collectors.toSet());
      if (!filesToProceed.isEmpty()) {
        // deepcode ignore checkIsPresent~Optional: collection already checked to be not empty
        final PsiFile firstFile = filesToProceed.stream().findFirst().get();
        info(
            "Files to proceed (not found in cache): "
                + filesToProceed.size()
                // fixme debug only
                + "\nHash for first file "
                + firstFile.getName()
                + " ["
                + HashContentUtils.getHash(firstFile)
                + "]");

        mapFile2Suggestions.putAll(retrieveSuggestions(project, filesToProceed, filesToRemove));

      } else if (!filesToRemove.isEmpty()) {
        info("Files to remove: " + filesToRemove.size() + " files: " + filesToRemove.toString());
        retrieveSuggestions(project, filesToProceed, filesToRemove);
      } else {
        warn("Nothing to update for " + psiFiles.size() + " files: " + psiFiles.toString());
      }

      ServiceManager.getService(project, myTodoView.class).refresh();
      updateInProgress = false;

    } finally {
      // if (filesToProceed != null && !filesToProceed.isEmpty())
      info("MUTEX RELEASED");
      MUTEX.unlock();
    }
  }

  private static boolean loginRequested = false;

  private static boolean isNotSucceed(
      @NotNull Project project, EmptyResponse response, String message) {
    if (response.getStatusCode() == 200) {
      loginRequested = false;
      return false;
    } else if (response.getStatusCode() == 401) {
      LoginUtils.isLogged(project, !loginRequested);
      loginRequested = true;
    }
    warn(message + response.getStatusCode() + " " + response.getStatusDescription());
    return true;
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> retrieveSuggestions(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull Collection<PsiFile> filesToRemove) {
    if (psiFiles.isEmpty() && filesToRemove.isEmpty()) {
      return Collections.emptyMap();
    }
    if (!LoginUtils.isLogged(project, false)) {
      return Collections.emptyMap();
    }
    Map<PsiFile, List<SuggestionForFile>> result;
    ProgressIndicator progress =
        ProgressManager.getInstance().getProgressIndicator(); // new StatusBarProgress();
    progress.setIndeterminate(false);
    //    progress.start();

    long startTime;
    // ---------------------------------------- Create Bundle
    startTime = System.currentTimeMillis();
    progress.setText(PREPARE_FILES_TEXT);
    info(PREPARE_FILES_TEXT);
    ProgressManager.checkCanceled();
    Map<String, String> mapPath2Hash = new HashMap<>();
    long sizePath2Hash = 0;
    int fileCounter = 0;
    int totalFiles = psiFiles.size();
    for (PsiFile file : psiFiles) {
      HashContentUtils.removeHashContent(file);
      ProgressManager.checkCanceled();
      progress.setFraction(((double) fileCounter++) / totalFiles);
      progress.setText(PREPARE_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");
      final String path = DeepCodeUtils.getDeepCodedFilePath(file);
      // info("getHash requested");
      final String hash = HashContentUtils.getHash(file);
      // info("getHash done");
      mapPath2Hash.put(path, hash);
      sizePath2Hash += (path.length() + hash.length()) * 2; // rough estimation of bytes occupied
      if (sizePath2Hash > MAX_BUNDLE_SIZE) {
        CreateBundleResponse tempBundleResponse =
            makeNewBundle(project, mapPath2Hash, Collections.emptyList());
        if (isNotSucceed(project, tempBundleResponse, "Bad Create/Extend Bundle request: "))
          return EMPTY_MAP;
        sizePath2Hash = 0;
        mapPath2Hash.clear();
      }
    }
    // todo break removeFiles in chunks less then MAX_BANDLE_SIZE
    CreateBundleResponse createBundleResponse = makeNewBundle(project, mapPath2Hash, filesToRemove);
    if (isNotSucceed(project, createBundleResponse, "Bad Create/Extend Bundle request: "))
      return EMPTY_MAP;
    final String bundleId = createBundleResponse.getBundleId();
    List<String> missingFiles = createBundleResponse.getMissingFiles();
    info(
        "--- Create/Extend Bundle took: "
            + (System.currentTimeMillis() - startTime)
            + " milliseconds"
            + "\nbundleId: "
            + bundleId
            + "\nmissingFiles: "
            + missingFiles.size());

    // ---------------------------------------- Upload Files
    startTime = System.currentTimeMillis();
    progress.setText(UPLOADING_FILES_TEXT);
    ProgressManager.checkCanceled();

    final int attempts = 5;
    for (int counter = 0; counter < attempts; counter++) {
      uploadFiles(project, psiFiles, missingFiles, bundleId, progress);
      missingFiles = checkBundle(project, bundleId, progress);
      if (missingFiles.isEmpty()) {
        break;
      } else {
        warn(
            "Check Bundle found some missingFiles to be NOT uploaded, will try to upload "
                + (attempts - counter)
                + " more times:\nmissingFiles = "
                + missingFiles);
      }
    }
    //    mapPsiFile2Hash.clear();
    // HashContentUtils.mapPsiFile2Content.clear();
    info("--- Upload Files took: " + (System.currentTimeMillis() - startTime) + " milliseconds");

    // ---------------------------------------- Get Analysis
    startTime = System.currentTimeMillis();
    progress.setText(WAITING_FOR_ANALYSIS_TEXT);
    ProgressManager.checkCanceled();
    GetAnalysisResponse getAnalysisResponse = doGetAnalysis(project, bundleId, progress);
    result = parseGetAnalysisResponse(project, psiFiles, getAnalysisResponse, progress);
    info("--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    //    progress.stop();
    return result;
  }

  private static void uploadFiles(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull List<String> missingFiles,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progress) {
    int fileCounter = 0;
    int totalFiles = missingFiles.size();
    long fileChunkSize = 0;
    int brokenMissingFilesCount = 0;
    String brokenMissingFilesMessage = "";
    List<PsiFile> filesChunk = new ArrayList<>();
    for (String filePath : missingFiles) {
      ProgressManager.checkCanceled();
      progress.checkCanceled();
      progress.setFraction(((double) fileCounter++) / totalFiles);
      progress.setText(UPLOADING_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");
      PsiFile psiFile =
          psiFiles.stream()
              .filter(f -> DeepCodeUtils.getDeepCodedFilePath(f).equals(filePath))
              .findAny()
              .orElse(null);
      if (psiFile == null) {
        if (brokenMissingFilesCount == 0) {
          brokenMissingFilesMessage =
              " files requested in missingFiles not found in psiFiles (skipped to upload)."
                  + "\nFirst broken missingFile: "
                  + filePath
                  + "\nFull file path example: "
                  // deepcode ignore checkIsPresent~Optional: collection already is not empty
                  + psiFiles.stream().findFirst().get().getVirtualFile().getPath()
                  + "\nBaseDir path: "
                  + project.getBasePath();
        }
        brokenMissingFilesCount++;
        continue;
      }
      final long fileSize = psiFile.getVirtualFile().getLength();
      if (fileChunkSize + fileSize > MAX_BUNDLE_SIZE) {
        info("Files-chunk size: " + fileChunkSize);
        doUploadFiles(project, filesChunk, bundleId, progress);
        fileChunkSize = 0;
        filesChunk.clear();
      }
      fileChunkSize += fileSize;
      filesChunk.add(psiFile);
    }
    if (brokenMissingFilesCount > 0) warn(brokenMissingFilesCount + brokenMissingFilesMessage);
    info("Last files-chunk size: " + fileChunkSize);
    doUploadFiles(project, filesChunk, bundleId, progress);
  }

  /**
   * Checks the status of a bundle: if there are still missing files after uploading
   *
   * @return list of the current missingFiles.
   */
  @NotNull
  private static List<String> checkBundle(
      @NotNull Project project, @NotNull String bundleId, @NotNull ProgressIndicator progress) {
    CreateBundleResponse checkBundleResponse =
        DeepCodeRestApi.checkBundle(DeepCodeParams.getSessionToken(), bundleId);
    if (isNotSucceed(project, checkBundleResponse, "Bad CheckBundle request: ")) {
      return Collections.emptyList();
    }
    return checkBundleResponse.getMissingFiles();
  }

  private static CreateBundleResponse makeNewBundle(
      @NotNull Project project,
      @NotNull Map<String, String> mapPath2Hash,
      @NotNull Collection<PsiFile> filesToRemove) {
    final FileHashRequest fileHashRequest = new FileHashRequest(mapPath2Hash);
    final String parentBundleId = mapProject2BundleId.getOrDefault(project, "");
    if (!parentBundleId.isEmpty()
        && !filesToRemove.isEmpty()
        && mapPath2Hash.isEmpty()
        && filesToRemove.containsAll(cachedFilesOfProject(project))) {
      warn(
          "Attempt to Extending a bundle by removing all the parent bundle's files: "
              + filesToRemove);
    }
    List<String> removedFiles =
        filesToRemove.stream()
            .map(DeepCodeUtils::getDeepCodedFilePath)
            .collect(Collectors.toList());
    String message =
        (parentBundleId.isEmpty()
                ? "Creating new Bundle with "
                : "Extending existing Bundle [" + parentBundleId + "] with ")
            + mapPath2Hash.size()
            + " files"
            + (removedFiles.isEmpty() ? "" : " and remove " + removedFiles.size() + " files");
    info(message);
    final CreateBundleResponse bundleResponse;
    // check if bundleID for the project already been created
    if (parentBundleId.isEmpty())
      bundleResponse =
          DeepCodeRestApi.createBundle(DeepCodeParams.getSessionToken(), fileHashRequest);
    else {
      bundleResponse =
          DeepCodeRestApi.extendBundle(
              DeepCodeParams.getSessionToken(),
              parentBundleId,
              new ExtendBundleRequest(fileHashRequest.getFiles(), removedFiles));
    }
    String newBundleId = bundleResponse.getBundleId();
    // By man: "Extending a bundle by removing all the parent bundle's files is not allowed."
    // In reality new bundle returned with next bundleID:
    // gh/ArtsiomCh/DEEPCODE_PRIVATE_BUNDLE/0000000000000000000000000000000000000000000000000000000000000000
    if (newBundleId.equals(
        "gh/ArtsiomCh/DEEPCODE_PRIVATE_BUNDLE/0000000000000000000000000000000000000000000000000000000000000000")) {
      newBundleId = "";
    }
    mapProject2BundleId.put(project, newBundleId);
    return bundleResponse;
  }

  private static void doUploadFiles(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progress) {
    List<FileHash2ContentRequest> listHash2Content = new ArrayList<>(psiFiles.size());
    info("Uploading " + psiFiles.size() + " files... ");
    for (PsiFile psiFile : psiFiles) {
      progress.checkCanceled();
      listHash2Content.add(new FileHash2ContentRequest(HashContentUtils.getHash(psiFile), HashContentUtils.getFileContent(psiFile)));
      //      logDeepCode("Uploading file: " + getPath(psiFile));
    }
    if (listHash2Content.isEmpty()) return;

    EmptyResponse uploadFilesResponse =
        DeepCodeRestApi.UploadFiles(DeepCodeParams.getSessionToken(), bundleId, listHash2Content);
    isNotSucceed(project, uploadFilesResponse, "Bad UploadFiles request: ");
  }

  @NotNull
  private static GetAnalysisResponse doGetAnalysis(
      @NotNull Project project,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progressIndicator) {
    GetAnalysisResponse response;
    int counter = 0;
    do {
      if (counter > 0) RunUtils.delay(500);
      response =
          DeepCodeRestApi.getAnalysis(
              DeepCodeParams.getSessionToken(),
              bundleId,
              DeepCodeParams.getMinSeverity(),
              DeepCodeParams.useLinter());

      ProgressManager.checkCanceled();
      info(response.toString());
      if (isNotSucceed(project, response, "Bad GetAnalysis request: "))
        return new GetAnalysisResponse();
      ProgressManager.checkCanceled();
      double progress = response.getProgress();
      if (progress == 0) progress = ((double) counter) / 200;
      progressIndicator.setFraction(progress);
      progressIndicator.setText(WAITING_FOR_ANALYSIS_TEXT + (int) (progress * 100) + "% done");
      // fixme
      if (counter == 200) break;
      if (response.getStatus().equals("FAILED")) {
        warn("FAILED getAnalysis request. Full project rescan requested.");
        RunUtils.rescanInBackgroundCancellableDelayed(project, 500, false);
      }
      counter++;
    } while (!response.getStatus().equals("DONE")
    // !!!! keep commented in production, for debug only: to emulate long processing
    // || counter < 10
    );
    return response;
  }

  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> parseGetAnalysisResponse(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      GetAnalysisResponse response,
      @NotNull ProgressIndicator progressIndicator) {
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    if (!response.getStatus().equals("DONE")) return EMPTY_MAP;
    AnalysisResults analysisResults = response.getAnalysisResults();
    mapProject2analysisUrl.put(project, response.getAnalysisURL());
    if (analysisResults == null) {
      warn("AnalysisResults is null for: " + response);
      return EMPTY_MAP;
    }
    for (PsiFile psiFile : psiFiles) {
      // fixme iterate over analysisResults.getFiles() to reduce empty passes
      FileSuggestions fileSuggestions =
          analysisResults.getFiles().get(DeepCodeUtils.getDeepCodedFilePath(psiFile));
      if (fileSuggestions == null) {
        result.put(psiFile, Collections.emptyList());
        continue;
      }
      final Suggestions suggestions = analysisResults.getSuggestions();
      if (suggestions == null) {
        warn("Suggestions is empty for: " + response);
        return EMPTY_MAP;
      }
      ProgressManager.checkCanceled();
      // fixme debug only
      // DCLogger.info("parseGetAnalysisResponse before Document requested");
      Document document =
          RunUtils.computeInReadActionInSmartMode(
              psiFile.getProject(), psiFile.getViewProvider()::getDocument);
      // fixme debug only
      // DCLogger.info("parseGetAnalysisResponse after Document requested");
      if (document == null) {
        warn("Document not found for file: " + psiFile + "\nGetAnalysisResponse: " + response);
        return EMPTY_MAP;
      }

      final List<SuggestionForFile> mySuggestions = new ArrayList<>();
      for (String suggestionIndex : fileSuggestions.keySet()) {
        final Suggestion suggestion = suggestions.get(suggestionIndex);
        if (suggestion == null) {
          warn(
              "Suggestion not found for suggestionIndex: "
                  + suggestionIndex
                  + "\nGetAnalysisResponse: "
                  + response);
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
    return new FileContent(DeepCodeUtils.getDeepCodedFilePath(psiFile), HashContentUtils.getFileContent(psiFile));
  }

  public static Set<PsiFile> getAllFilesWithSuggestions(@NotNull final Project project) {
    return mapFile2Suggestions.entrySet().stream()
        .filter(e -> e.getKey().getProject().equals(project))
        .filter(e -> !e.getValue().isEmpty())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public static boolean isFileInCache(@NotNull PsiFile psiFile) {
    return mapFile2Suggestions.containsKey(psiFile);
  }

  /** Remove project from all Caches and <b>CANCEL</b> all background tasks for it */
  public static void resetCachesAndTasks(@Nullable final Project project) {
    final Set<Project> projects =
        (project == null) ? getAllCachedProject() : Collections.singleton(project);
    for (Project prj : projects) {
      // lets all running ProgressIndicators release MUTEX first
      RunUtils.cancelRunningIndicators(prj);
      removeProjectFromCaches(prj);
      ServiceManager.getService(prj, myTodoView.class).refresh();
      mapProject2analysisUrl.put(prj, "");
    }
  }
}
