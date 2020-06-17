package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.*;
import ai.deepcode.javaclient.responses.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public abstract class AnalysisDataBase {

  private final PlatformDependentUtilsBase pdUtils;
  private final HashContentUtilsBase hashContentUtils;
  private final DeepCodeParamsBase deepCodeParams;
  private final DCLoggerBase dcLogger;

  protected AnalysisDataBase(
      @NotNull PlatformDependentUtilsBase platformDependentUtils,
      @NotNull HashContentUtilsBase hashContentUtils,
      @NotNull DeepCodeParamsBase deepCodeParams,
      @NotNull DCLoggerBase dcLogger) {
    this.pdUtils = platformDependentUtils;
    this.hashContentUtils = hashContentUtils;
    this.deepCodeParams = deepCodeParams;
    this.dcLogger = dcLogger;
  }

  private static final String UPLOADING_FILES_TEXT = "DeepCode: Uploading files to the server... ";
  private static final String PREPARE_FILES_TEXT = "DeepCode: Preparing files for upload... ";
  private static final String WAITING_FOR_ANALYSIS_TEXT =
      "DeepCode: Waiting for analysis from server... ";

  //  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalysisData");
  private static final Map<Object, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();
  private static final Map<Object, String> mapProject2analysisUrl = new ConcurrentHashMap<>();

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<Object, List<SuggestionForFile>> mapFile2Suggestions =
      new ConcurrentHashMap<>();

  private static final Map<Object, String> mapProject2BundleId = new ConcurrentHashMap<>();

  // Mutex need to be requested to change mapFile2Suggestions
  private static final ReentrantLock MUTEX = new ReentrantLock();

  /** see getAnalysis() below} */
  @NotNull
  public List<SuggestionForFile> getAnalysis(@NotNull Object file) {
    return getAnalysis(Collections.singleton(file)).getOrDefault(file, Collections.emptyList());
  }

  /**
   * Return Suggestions mapped to Files.
   *
   * <p>Look into cached results ONLY.
   *
   * @param files
   * @return
   */
  @NotNull
  public Map<Object, List<SuggestionForFile>> getAnalysis(@NotNull Collection<Object> files) {
    if (files.isEmpty()) {
      dcLogger.logWarn("getAnalysis requested for empty list of files");
      return Collections.emptyMap();
    }
    Map<Object, List<SuggestionForFile>> result = new HashMap<>();
    final Collection<Object> brokenKeys = new ArrayList<>();
    for (Object file : files) {
      List<SuggestionForFile> suggestions = mapFile2Suggestions.get(file);
      if (suggestions != null) {
        result.put(file, suggestions);
      } else {
        brokenKeys.add(file);
      }
    }
    if (!brokenKeys.isEmpty()) {
      dcLogger.logWarn(
          "Suggestions not found for " + brokenKeys.size() + " files: " + brokenKeys.toString());
    }
    return result;
  }

  public String getAnalysisUrl(@NotNull Object project) {
    return mapProject2analysisUrl.computeIfAbsent(project, p -> "");
  }

  public boolean addProjectToCache(@NotNull Object project) {
    return mapProject2BundleId.putIfAbsent(project, "") == null;
  }

  public Set<Object> getAllCachedProject() {
    return mapProject2BundleId.keySet();
  }

  public void removeFilesFromCache(@NotNull Collection<Object> files) {
    try {
      dcLogger.logInfo("Request to remove from cache " + files.size() + " files: " + files);
      // todo: do we really need mutex here?
      MUTEX.lock();
      dcLogger.logInfo("MUTEX LOCK");
      int removeCounter = 0;
      for (Object file : files) {
        if (file != null && isFileInCache(file)) {
          mapFile2Suggestions.remove(file);
          hashContentUtils.removeFileHashContent(file);
          removeCounter++;
        }
      }
      dcLogger.logInfo(
          "Actually removed from cache: "
              + removeCounter
              + " files. Were not in cache: "
              + (files.size() - removeCounter));
    } finally {
      dcLogger.logInfo("MUTEX RELEASED");
      MUTEX.unlock();
    }
  }

  public void removeProjectFromCaches(@NotNull Object project) {
    dcLogger.logInfo("Caches clearance requested for project: " + project);
    hashContentUtils.removeProjectHashContent(project);
    if (mapProject2BundleId.remove(project) != null) {
      dcLogger.logInfo("Removed from cache: " + project);
    }
    removeFilesFromCache(cachedFilesOfProject(project));
  }

  private Collection<Object> cachedFilesOfProject(@NotNull Object project) {
    return mapFile2Suggestions.keySet().stream()
        .filter(file -> pdUtils.getProject(file).equals(project))
        .collect(Collectors.toList());
  }

  private static boolean updateInProgress = true;

  public void setUpdateInProgress() {
    updateInProgress = true;
  }

  public boolean isUpdateAnalysisInProgress() {
    return updateInProgress;
  }

  public boolean isAnalysisResultsNOTAvailable(@NotNull Object project) {
    final boolean projectWasNotAnalysed = !getAllCachedProject().contains(project);
    return projectWasNotAnalysed || isUpdateAnalysisInProgress();
  }

  public void waitForUpdateAnalysisFinish() {
    while (updateInProgress) {
      // delay should be less or equal to runInBackgroundCancellable delay
      pdUtils.delay(pdUtils.DEFAULT_DELAY_SMALL);
    }
  }

  /*
    public static void updateCachedResultsForFile(@NotNull Object psiFile) {
      updateCachedResultsForFiles(Collections.singleton(psiFile), Collections.emptyList());
    }
  */

  public void updateCachedResultsForFiles(
      @NotNull Object project,
      @NotNull Collection<Object> psiFiles,
      @NotNull Collection<Object> filesToRemove) {
    if (psiFiles.isEmpty() && filesToRemove.isEmpty()) {
      dcLogger.logWarn("updateCachedResultsForFiles requested for empty list of files");
      return;
    }
    dcLogger.logInfo("Update requested for " + psiFiles.size() + " files: " + psiFiles.toString());
    if (!deepCodeParams.consentGiven(project)) {
      dcLogger.logWarn("Consent check fail! Object: " + pdUtils.getProjectName(project));
      return;
    }
    try {
      MUTEX.lock();
      dcLogger.logInfo("MUTEX LOCK");
      updateInProgress = true;
      Collection<Object> filesToProceed =
          psiFiles.stream()
              .filter(Objects::nonNull)
              .filter(file -> !mapFile2Suggestions.containsKey(file))
              .collect(Collectors.toSet());
      if (!filesToProceed.isEmpty()) {
        // collection already checked to be not empty
        final Object firstFile = filesToProceed.iterator().next();
        final String fileHash = hashContentUtils.getHash(firstFile);
        dcLogger.logInfo(
            "Files to proceed (not found in cache): "
                + filesToProceed.size()
                + "\nHash for first file "
                + pdUtils.getFileName(firstFile)
                + " ["
                + fileHash
                + "]");
        if (filesToProceed.size() == 1 && filesToRemove.isEmpty()) {
          // if only one file updates then its most likely from annotator. So we need to get
          // suggestions asap:
          // we do that through createBundle with fileContent
          mapFile2Suggestions.put(firstFile, retrieveSuggestions(firstFile));
          // and then request normal extendBundle later to synchronize results on server
          pdUtils.runInBackgroundCancellable(
              firstFile, () -> retrieveSuggestions(project, filesToProceed, filesToRemove));
        } else {
          mapFile2Suggestions.putAll(retrieveSuggestions(project, filesToProceed, filesToRemove));
        }
      } else if (!filesToRemove.isEmpty()) {
        dcLogger.logInfo(
            "Files to remove: " + filesToRemove.size() + " files: " + filesToRemove.toString());
        retrieveSuggestions(project, filesToProceed, filesToRemove);
      } else {
        dcLogger.logWarn(
            "Nothing to update for " + psiFiles.size() + " files: " + psiFiles.toString());
      }
      updateInProgress = false;
      pdUtils.refreshPanel(project);
      // ServiceManager.getService(project, myTodoView.class).refresh();
    } finally {
      // if (filesToProceed != null && !filesToProceed.isEmpty())
      dcLogger.logInfo("MUTEX RELEASED");
      MUTEX.unlock();
    }
  }

  private static boolean loginRequested = false;

  private boolean isNotSucceed(@NotNull Object project, EmptyResponse response, String message) {
    if (response.getStatusCode() == 200) {
      loginRequested = false;
      return false;
    } else if (response.getStatusCode() == 401) {
      pdUtils.isLogged(project, !loginRequested);
      loginRequested = true;
    }
    dcLogger.logWarn(message + response.getStatusCode() + " " + response.getStatusDescription());
    return true;
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private Map<Object, List<SuggestionForFile>> retrieveSuggestions(
      @NotNull Object project,
      @NotNull Collection<Object> filesToProceed,
      @NotNull Collection<Object> filesToRemove) {
    if (filesToProceed.isEmpty() && filesToRemove.isEmpty()) {
      dcLogger.logWarn("Both filesToProceed and filesToRemove are empty");
      return EMPTY_MAP;
    }
    // no needs to check login here as it will be checked anyway during every api response's check
    // if (!LoginUtils.isLogged(project, false)) return EMPTY_MAP;

    List<String> missingFiles = createBundleStep(project, filesToProceed, filesToRemove);

    uploadFilesStep(project, filesToProceed, missingFiles);

    // ---------------------------------------- Get Analysis
    final String bundleId = mapProject2BundleId.getOrDefault(project, "");
    if (bundleId.isEmpty()) return EMPTY_MAP; // no sense to proceed without bundleId
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(WAITING_FOR_ANALYSIS_TEXT);
    pdUtils.progressCheckCanceled();
    GetAnalysisResponse getAnalysisResponse = doGetAnalysis(project, bundleId);
    Map<Object, List<SuggestionForFile>> result =
        parseGetAnalysisResponse(project, filesToProceed, getAnalysisResponse);
    dcLogger.logInfo(
        "--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    return result;
  }

  /**
   * Perform costly network request. <b>No cache checks!</b>
   *
   * @return missingFiles
   */
  private List<String> createBundleStep(
      @NotNull Object project,
      @NotNull Collection<Object> filesToProceed,
      @NotNull Collection<Object> filesToRemove) {
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(PREPARE_FILES_TEXT);
    dcLogger.logInfo(PREPARE_FILES_TEXT);
    pdUtils.progressCheckCanceled();
    Map<String, String> mapPath2Hash = new HashMap<>();
    long sizePath2Hash = 0;
    int fileCounter = 0;
    int totalFiles = filesToProceed.size();
    for (Object file : filesToProceed) {
      hashContentUtils.removeFileHashContent(file);
      pdUtils.progressCheckCanceled();
      pdUtils.progressSetFraction(((double) fileCounter++) / totalFiles);
      pdUtils.progressSetText(
          PREPARE_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");
      final String path = pdUtils.getDeepCodedFilePath(file);
      // info("getHash requested");
      final String hash = hashContentUtils.getHash(file);
      // info("getHash done");
      mapPath2Hash.put(path, hash);
      sizePath2Hash += (path.length() + hash.length()) * 2; // rough estimation of bytes occupied
      if (sizePath2Hash > MAX_BUNDLE_SIZE) {
        CreateBundleResponse tempBundleResponse =
            makeNewBundle(project, mapPath2Hash, Collections.emptyList());
        sizePath2Hash = 0;
        mapPath2Hash.clear();
      }
    }
    // todo break removeFiles in chunks less then MAX_BANDLE_SIZE
    //  needed ?? we do full rescan for large amount of files to remove
    CreateBundleResponse createBundleResponse = makeNewBundle(project, mapPath2Hash, filesToRemove);

    final String bundleId = createBundleResponse.getBundleId();

    List<String> missingFiles = createBundleResponse.getMissingFiles();
    dcLogger.logInfo(
        "--- Create/Extend Bundle took: "
            + (System.currentTimeMillis() - startTime)
            + " milliseconds"
            + "\nbundleId: "
            + bundleId
            + "\nmissingFiles: "
            + missingFiles.size());
    return missingFiles;
  }

  /** Perform costly network request. <b>No cache checks!</b> */
  private void uploadFilesStep(
      @NotNull Object project,
      @NotNull Collection<Object> filesToProceed,
      @NotNull List<String> missingFiles) {
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(UPLOADING_FILES_TEXT);
    pdUtils.progressCheckCanceled();

    final String bundleId = mapProject2BundleId.getOrDefault(project, "");
    if (bundleId.isEmpty()) {
      dcLogger.logInfo("BundleId is empty");
    } else if (missingFiles.isEmpty()) {
      dcLogger.logInfo("No missingFiles to Upload");
    } else {
      final int attempts = 5;
      for (int counter = 0; counter < attempts; counter++) {
        uploadFiles(project, filesToProceed, missingFiles, bundleId);
        missingFiles = checkBundle(project, bundleId);
        if (missingFiles.isEmpty()) {
          break;
        } else {
          dcLogger.logWarn(
              "Check Bundle found "
                  + missingFiles.size()
                  + " missingFiles (NOT uploaded), will try to upload "
                  + (attempts - counter)
                  + " more times:\nmissingFiles = "
                  + missingFiles);
        }
      }
    }
    dcLogger.logInfo(
        "--- Upload Files took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
  }

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private List<SuggestionForFile> retrieveSuggestions(@NotNull Object file) {
    final Object project = pdUtils.getProject(file);
    List<SuggestionForFile> result;
    long startTime;
    // ---------------------------------------- Create Bundle
    startTime = System.currentTimeMillis();
    dcLogger.logInfo("Creating temporary Bundle from File content");
    pdUtils.progressCheckCanceled();

    FileContent fileContent =
        new FileContent(pdUtils.getDeepCodedFilePath(file), hashContentUtils.getFileContent(file));
    FileContentRequest fileContentRequest =
        new FileContentRequest(Collections.singletonList(fileContent));

    // todo?? it might be cheaper on server side to extend one temporary bundle
    //  rather then create the new one every time
    final CreateBundleResponse createBundleResponse =
        DeepCodeRestApi.createBundle(deepCodeParams.getSessionToken(), fileContentRequest);
    isNotSucceed(project, createBundleResponse, "Bad Create/Extend Bundle request: ");

    final String bundleId = createBundleResponse.getBundleId();
    if (bundleId.isEmpty()) return Collections.emptyList(); // no sense to proceed without bundleId

    List<String> missingFiles = createBundleResponse.getMissingFiles();
    dcLogger.logInfo(
        "--- Create temporary Bundle took: "
            + (System.currentTimeMillis() - startTime)
            + " milliseconds"
            + "\nbundleId: "
            + bundleId
            + "\nmissingFiles: "
            + missingFiles);
    if (!missingFiles.isEmpty()) dcLogger.logWarn("missingFiles is NOT empty!");

    // ---------------------------------------- Get Analysis
    pdUtils.progressCheckCanceled();
    startTime = System.currentTimeMillis();
    GetAnalysisResponse getAnalysisResponse = doGetAnalysis(project, bundleId);
    result =
        parseGetAnalysisResponse(project, Collections.singleton(file), getAnalysisResponse)
            .getOrDefault(file, Collections.emptyList());
    mapProject2analysisUrl.put(project, "");

    dcLogger.logInfo(
        "--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    //    progress.stop();
    return result;
  }

  private void uploadFiles(
      @NotNull Object project,
      @NotNull Collection<Object> filesToProceed,
      @NotNull List<String> missingFiles,
      @NotNull String bundleId) {
    Map<String, Object> mapPath2File =
        filesToProceed.stream().collect(Collectors.toMap(pdUtils::getDeepCodedFilePath, it -> it));
    int fileCounter = 0;
    int totalFiles = missingFiles.size();
    long fileChunkSize = 0;
    int brokenMissingFilesCount = 0;
    String brokenMissingFilesMessage = "";
    List<Object> filesChunk = new ArrayList<>();
    for (String filePath : missingFiles) {
      pdUtils.progressCheckCanceled();
      pdUtils.progressSetFraction(((double) fileCounter++) / totalFiles);
      pdUtils.progressSetText(
          UPLOADING_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");

      Object file = mapPath2File.get(filePath);
      if (file == null) {
        if (brokenMissingFilesCount == 0) {
          brokenMissingFilesMessage =
              " files requested in missingFiles not found in filesToProceed (skipped to upload)."
                  + "\nFirst broken missingFile: "
                  + filePath;
        }
        brokenMissingFilesCount++;
        continue;
      }
      final long fileSize = pdUtils.getFileSize(file); // .getVirtualFile().getLength();
      if (fileChunkSize + fileSize > MAX_BUNDLE_SIZE) {
        dcLogger.logInfo("Files-chunk size: " + fileChunkSize);
        doUploadFiles(project, filesChunk, bundleId);
        fileChunkSize = 0;
        filesChunk.clear();
      }
      fileChunkSize += fileSize;
      filesChunk.add(file);
    }
    if (brokenMissingFilesCount > 0)
      dcLogger.logWarn(brokenMissingFilesCount + brokenMissingFilesMessage);
    dcLogger.logInfo("Last filesToProceed-chunk size: " + fileChunkSize);
    doUploadFiles(project, filesChunk, bundleId);
  }

  /**
   * Checks the status of a bundle: if there are still missing files after uploading
   *
   * @return list of the current missingFiles.
   */
  @NotNull
  private List<String> checkBundle(@NotNull Object project, @NotNull String bundleId) {
    CreateBundleResponse checkBundleResponse =
        DeepCodeRestApi.checkBundle(deepCodeParams.getSessionToken(), bundleId);
    if (isNotSucceed(project, checkBundleResponse, "Bad CheckBundle request: ")) {
      return Collections.emptyList();
    }
    return checkBundleResponse.getMissingFiles();
  }

  private CreateBundleResponse makeNewBundle(
      @NotNull Object project,
      @NotNull Map<String, String> mapPath2Hash,
      @NotNull Collection<Object> filesToRemove) {
    final FileHashRequest fileHashRequest = new FileHashRequest(mapPath2Hash);
    final String parentBundleId = mapProject2BundleId.getOrDefault(project, "");
    if (!parentBundleId.isEmpty()
        && !filesToRemove.isEmpty()
        && mapPath2Hash.isEmpty()
        && filesToRemove.containsAll(cachedFilesOfProject(project))) {
      dcLogger.logWarn(
          "Attempt to Extending a bundle by removing all the parent bundle's files: "
              + filesToRemove);
    }
    List<String> removedFiles =
        filesToRemove.stream().map(pdUtils::getDeepCodedFilePath).collect(Collectors.toList());
    String message =
        (parentBundleId.isEmpty()
                ? "Creating new Bundle with "
                : "Extending existing Bundle [" + parentBundleId + "] with ")
            + mapPath2Hash.size()
            + " files"
            + (removedFiles.isEmpty() ? "" : " and remove " + removedFiles.size() + " files");
    dcLogger.logInfo(message);
    // todo make network request in parallel with collecting data
    final CreateBundleResponse bundleResponse;
    // check if bundleID for the project already been created
    if (parentBundleId.isEmpty())
      bundleResponse =
          DeepCodeRestApi.createBundle(deepCodeParams.getSessionToken(), fileHashRequest);
    else {
      bundleResponse =
          DeepCodeRestApi.extendBundle(
              deepCodeParams.getSessionToken(),
              parentBundleId,
              new ExtendBundleRequest(fileHashRequest.getFiles(), removedFiles));
    }
    String newBundleId = bundleResponse.getBundleId();
    // By man: "Extending a bundle by removing all the parent bundle's files is not allowed."
    // In reality new bundle returned with next bundleID:
    // gh/ArtsiomCh/DEEPCODE_PRIVATE_BUNDLE/0000000000000000000000000000000000000000000000000000000000000000
    if (newBundleId.endsWith(
        "/DEEPCODE_PRIVATE_BUNDLE/0000000000000000000000000000000000000000000000000000000000000000")) {
      newBundleId = "";
    }
    mapProject2BundleId.put(project, newBundleId);
    isNotSucceed(project, bundleResponse, "Bad Create/Extend Bundle request: ");
    return bundleResponse;
  }

  private void doUploadFiles(
      @NotNull Object project, @NotNull Collection<Object> psiFiles, @NotNull String bundleId) {
    dcLogger.logInfo("Uploading " + psiFiles.size() + " files... ");
    if (psiFiles.isEmpty()) return;
    List<FileHash2ContentRequest> listHash2Content = new ArrayList<>(psiFiles.size());
    for (Object psiFile : psiFiles) {
      pdUtils.progressCheckCanceled();
      listHash2Content.add(
          new FileHash2ContentRequest(
              hashContentUtils.getHash(psiFile), hashContentUtils.getFileContent(psiFile)));
    }
    if (listHash2Content.isEmpty()) return;

    // todo make network request in parallel with collecting data
    EmptyResponse uploadFilesResponse =
        DeepCodeRestApi.UploadFiles(deepCodeParams.getSessionToken(), bundleId, listHash2Content);
    isNotSucceed(project, uploadFilesResponse, "Bad UploadFiles request: ");
  }

  @NotNull
  private GetAnalysisResponse doGetAnalysis(@NotNull Object project, @NotNull String bundleId) {
    GetAnalysisResponse response;
    int counter = 0;
    final int timeout = 100; // seconds
    final int attempts = timeout * 1000 / pdUtils.DEFAULT_DELAY;
    do {
      if (counter > 0) pdUtils.delay(pdUtils.DEFAULT_DELAY);
      response =
          DeepCodeRestApi.getAnalysis(
              deepCodeParams.getSessionToken(),
              bundleId,
              deepCodeParams.getMinSeverity(),
              deepCodeParams.useLinter());

      pdUtils.progressCheckCanceled();
      dcLogger.logInfo(response.toString());
      if (isNotSucceed(project, response, "Bad GetAnalysis request: "))
        return new GetAnalysisResponse();

      double progress = response.getProgress();
      if (progress <= 0 || progress > 1) progress = ((double) counter) / attempts;
      pdUtils.progressSetFraction(progress);
      pdUtils.progressSetText(WAITING_FOR_ANALYSIS_TEXT + (int) (progress * 100) + "% done");

      if (counter >= attempts) {
        dcLogger.logWarn("Timeout expire for waiting analysis results.");
        /*
                DeepCodeNotifications.showWarn(
                    "Can't get analysis results from the server. Network or server internal error. Please, try again later.",
                    project);
        */
        break;
      }

      if (response.getStatus().equals("FAILED")) {
        dcLogger.logWarn("FAILED getAnalysis request.");
        // if Failed then we have inconsistent caches, better to do full rescan
        pdUtils.doFullRescan(project);
        /*if (!RunUtils.isFullRescanRequested(project)) {
          RunUtils.rescanInBackgroundCancellableDelayed(project, 500, false);
        }*/
        break;
      }

      counter++;
    } while (!response.getStatus().equals("DONE")
    // !!!! keep commented in production, for debug only: to emulate long processing
    // || counter < 10
    );
    return response;
  }

  @NotNull
  private Map<Object, List<SuggestionForFile>> parseGetAnalysisResponse(
      @NotNull Object project, @NotNull Collection<Object> files, GetAnalysisResponse response) {
    Map<Object, List<SuggestionForFile>> result = new HashMap<>();
    if (!response.getStatus().equals("DONE")) return EMPTY_MAP;
    AnalysisResults analysisResults = response.getAnalysisResults();
    mapProject2analysisUrl.put(project, response.getAnalysisURL());
    if (analysisResults == null) {
      dcLogger.logWarn("AnalysisResults is null for: " + response);
      return EMPTY_MAP;
    }
    for (Object file : files) {
      // fixme iterate over analysisResults.getFiles() to reduce empty passes
      FileSuggestions fileSuggestions =
          analysisResults.getFiles().get(pdUtils.getDeepCodedFilePath(file));
      if (fileSuggestions == null) {
        result.put(file, Collections.emptyList());
        continue;
      }
      final Suggestions suggestions = analysisResults.getSuggestions();
      if (suggestions == null) {
        dcLogger.logWarn("Suggestions is empty for: " + response);
        return EMPTY_MAP;
      }
      pdUtils.progressCheckCanceled();

      final List<SuggestionForFile> mySuggestions = new ArrayList<>();
      for (String suggestionIndex : fileSuggestions.keySet()) {
        final Suggestion suggestion = suggestions.get(suggestionIndex);
        if (suggestion == null) {
          dcLogger.logWarn(
              "Suggestion not found for suggestionIndex: "
                  + suggestionIndex
                  + "\nGetAnalysisResponse: "
                  + response);
          return EMPTY_MAP;
        }
        final List<MyTextRange> ranges = new ArrayList<>();
        for (FileRange fileRange : fileSuggestions.get(suggestionIndex)) {
          final int startRow = fileRange.getRows().get(0);
          final int endRow = fileRange.getRows().get(1);
          final int startCol = fileRange.getCols().get(0) - 1; // inclusive
          final int endCol = fileRange.getCols().get(1);
          final int lineStartOffset = pdUtils.getLineStartOffset(file, startRow - 1); // to 0-based
          final int lineEndOffset = pdUtils.getLineStartOffset(file, endRow - 1);
          ranges.add(new MyTextRange(lineStartOffset + startCol, lineEndOffset + endCol));
        }
        mySuggestions.add(
            new SuggestionForFile(
                suggestion.getId(), suggestion.getMessage(), suggestion.getSeverity(), ranges));
      }
      result.put(file, mySuggestions);
    }
    return result;
  }

  private FileContent createFileContent(Object file) {
    return new FileContent(
        pdUtils.getDeepCodedFilePath(file), hashContentUtils.getFileContent(file));
  }

  public Set<Object> getAllFilesWithSuggestions(@NotNull final Object project) {
    return mapFile2Suggestions.entrySet().stream()
        .filter(e -> pdUtils.getProject(e.getKey()).equals(project))
        .filter(e -> !e.getValue().isEmpty())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public boolean isFileInCache(@NotNull Object psiFile) {
    return mapFile2Suggestions.containsKey(psiFile);
  }

  /** Remove project from all Caches and <b>CANCEL</b> all background tasks for it */
  public void resetCachesAndTasks(@Nullable final Object project) {
    final Set<Object> projects =
        (project == null) ? getAllCachedProject() : Collections.singleton(project);
    for (Object prj : projects) {
      // lets all running ProgressIndicators release MUTEX first
      pdUtils.cancelRunningIndicators(prj);
      removeProjectFromCaches(prj);
      pdUtils.refreshPanel(prj); // ServiceManager.getService(prj, myTodoView.class).refresh();
      mapProject2analysisUrl.put(prj, "");
    }
  }
}
