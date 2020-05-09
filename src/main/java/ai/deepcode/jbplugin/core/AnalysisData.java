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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ai.deepcode.jbplugin.core.DCLogger.info;

public final class AnalysisData {

  private static final String UPLOADING_FILES_TEXT = "DeepCode: Uploading files to the server... ";
  private static final String PREPARE_FILES_TEXT = "DeepCode: Preparing files for upload... ";
  private static final String WAITING_FOR_ANALYSIS_TEXT =
      "DeepCode: Waiting for analysis from server... ";

  private AnalysisData() {}

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalysisData");
  private static final Map<PsiFile, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();
  private static String analysisUrl = "";

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<PsiFile, List<SuggestionForFile>> mapFile2Suggestions =
      // deepcode ignore ApiMigration~java.util.Hashtable: we need read and write full data lock
      new Hashtable<>(); // new ConcurrentHashMap<>();

  private static final Map<PsiFile, String> mapPsiFile2Hash = new ConcurrentHashMap<>();

  private static final Map<Project, String> mapProject2BundleId = new ConcurrentHashMap<>();

  // Mutex need to be requested to change mapFile2Suggestions
  private static final ReentrantLock MUTEX = new ReentrantLock();

  public static String getAnalysisUrl() {
    return analysisUrl;
  }

  public static boolean isAnalysisInProgress() {
    return MUTEX.isLocked();
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
      MUTEX.lock();
      int removeCounter = 0;
      for (PsiFile file : files) {
        if (file != null && isFileInCache(file)) {
          mapFile2Suggestions.remove(file);
          mapPsiFile2Hash.remove(file);
          removeCounter++;
        }
      }
      info(
          "Actually removed from cache: "
              + removeCounter
              + " files. Were not in cache: "
              + (files.size() - removeCounter));
    } finally {
      MUTEX.unlock();
    }
  }

  static void removeProjectFromCache(@NotNull Project project) {
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

  /** see getAnalysis() below} */
  @NotNull
  public static List<SuggestionForFile> getAnalysis(@NotNull PsiFile psiFile) {
    return getAnalysis(Collections.singleton(psiFile), Collections.emptyList())
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
      @NotNull Collection<PsiFile> psiFiles, @NotNull Collection<PsiFile> filesToRemove) {
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    Collection<PsiFile> filesToProceed = null;
    try {
      MUTEX.lock();
      filesToProceed =
          // DeepCodeUtils.computeNonBlockingReadAction(
          // () ->
          psiFiles.stream()
              .filter(Objects::nonNull)
              // .filter(PsiFile::isValid)
              .filter(file -> !mapFile2Suggestions.containsKey(file))
              .collect(Collectors.toSet());
      if (!filesToProceed.isEmpty()) {
        info("Analysis requested for " + psiFiles.size() + " files: " + psiFiles.toString());
        // fixme debug only
        final PsiFile firstFile = psiFiles.stream().findFirst().get();
        info("Hash for " + firstFile.getName() + " [" + getHash(firstFile) + "]");
        info("Files to proceed (not found in cache): " + filesToProceed.size());

        mapFile2Suggestions.putAll(retrieveSuggestions(filesToProceed, filesToRemove));

      } else if (!filesToRemove.isEmpty()) {
        info("Files to remove: " + filesToRemove.size() + " files: " + filesToRemove.toString());
        retrieveSuggestions(filesToProceed, filesToRemove);
      }
    } finally {
      // fixme debug only
      if (filesToProceed != null && !filesToProceed.isEmpty()) info("MUTEX RELEASED");
      MUTEX.unlock();
    }
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
      info("Suggestions not found for " + brokenKeys.size() + " files: " + brokenKeys.toString());
    }
    return result;
  }

  private static boolean loginRequested = false;

  private static boolean isNotSucceed(
      @NotNull Project project, EmptyResponse response, String message) {
    if (response.getStatusCode() == 200) {
      loginRequested = false;
      return false;
    } else if (response.getStatusCode() == 401) {
      DeepCodeUtils.isLogged(project, !loginRequested);
      loginRequested = true;
    }
    info(message + response.getStatusCode() + " " + response.getStatusDescription());
    return true;
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes
  private static final Map<PsiFile, String> mapPsiFile2Content = new ConcurrentHashMap<>();

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> retrieveSuggestions(
      @NotNull Collection<PsiFile> psiFiles, @NotNull Collection<PsiFile> filesToRemove) {
    if (psiFiles.isEmpty() && filesToRemove.isEmpty()) {
      return Collections.emptyMap();
    }
    Project project =
        // fixme
        (psiFiles.isEmpty() ? filesToRemove : psiFiles).stream().findFirst().get().getProject();
    if (!DeepCodeUtils.isLogged(project, false)) {
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
    ProgressManager.checkCanceled();
    mapPsiFile2Content.clear();
    List<String> removedFiles =
        filesToRemove.stream()
            .map(DeepCodeUtils::getDeepCodedFilePath)
            .collect(Collectors.toList());
    Map<String, String> mapPath2Hash = new HashMap<>();
    long sizePath2Hash = 0;
    int fileCounter = 0;
    int totalFiles = psiFiles.size();
    for (PsiFile file : psiFiles) {
      mapPsiFile2Hash.remove(file);
      ProgressManager.checkCanceled();
      progress.setFraction(((double) fileCounter++) / totalFiles);
      progress.setText(PREPARE_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");
      final String path = DeepCodeUtils.getDeepCodedFilePath(file);
      final String hash = getHash(file);
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
    CreateBundleResponse createBundleResponse = makeNewBundle(project, mapPath2Hash, removedFiles);
    if (isNotSucceed(project, createBundleResponse, "Bad Create/Extend Bundle request: "))
      return EMPTY_MAP;
    info(
        "--- Create/Extend Bundle took: "
            + (System.currentTimeMillis() - startTime)
            + " milliseconds");

    final String bundleId = createBundleResponse.getBundleId();
    info("bundleId: " + bundleId);

    final List<String> missingFiles = createBundleResponse.getMissingFiles();
    info("missingFiles: " + missingFiles.size());

    // ---------------------------------------- Upload Files
    startTime = System.currentTimeMillis();
    progress.setText(UPLOADING_FILES_TEXT);
    ProgressManager.checkCanceled();

    fileCounter = 0;
    totalFiles = missingFiles.size();
    long fileChunkSize = 0;
    int brokenMissingFilesCount = 0;
    String brokenMissingFilesMessage = "";
    List<PsiFile> filesChunk = new ArrayList<>();
    for (String filePath : missingFiles) {
      ProgressManager.checkCanceled();
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
                  + " First broken missingFile: "
                  + filePath
                  + " Full file path example: "
                  + psiFiles.stream().findFirst().get().getVirtualFile().getPath()
                  + " BaseDir path: "
                  + project.getBasePath();
        }
        brokenMissingFilesCount++;
        continue;
      }
      final long fileSize = psiFile.getVirtualFile().getLength();
      if (fileChunkSize + fileSize > MAX_BUNDLE_SIZE) {
        info("Files-chunk size: " + fileChunkSize);
        uploadFiles(project, filesChunk, bundleId, progress);
        fileChunkSize = 0;
        filesChunk.clear();
      }
      fileChunkSize += fileSize;
      filesChunk.add(psiFile);
    }
    if (brokenMissingFilesCount > 0) info(brokenMissingFilesCount + brokenMissingFilesMessage);
    info("Last files-chunk size: " + fileChunkSize);
    uploadFiles(project, filesChunk, bundleId, progress);

    //    mapPsiFile2Hash.clear();
    mapPsiFile2Content.clear();
    info("--- Upload Files took: " + (System.currentTimeMillis() - startTime) + " milliseconds");

    // ---------------------------------------- Get Analysis
    startTime = System.currentTimeMillis();
    progress.setText(WAITING_FOR_ANALYSIS_TEXT);
    ProgressManager.checkCanceled();
    GetAnalysisResponse getAnalysisResponse = doRetrieveSuggestions(project, bundleId, progress);
    result = parseGetAnalysisResponse(psiFiles, getAnalysisResponse, progress);
    info("--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    //    progress.stop();
    return result;
  }

  private static CreateBundleResponse makeNewBundle(
      @NotNull Project project,
      @NotNull Map<String, String> mapPath2Hash,
      @NotNull List<String> removedFiles) {
    final FileHashRequest fileHashRequest = new FileHashRequest(mapPath2Hash);
    final String parentBundleId = mapProject2BundleId.getOrDefault(project, "");
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
    mapProject2BundleId.put(project, bundleResponse.getBundleId());
    return bundleResponse;
  }

  // https://www.baeldung.com/sha-256-hashing-java#message-digest
  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) hexString.append('0');
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /** check if Hash for PsiFile was changed comparing to cached hash */
  public static boolean isHashChanged(@NotNull PsiFile psiFile) {
    // fixme debug only
    // DCLogger.info("hash check started");
    String newHash = doGetHash(doGetFileContent(psiFile));
    String oldHash = mapPsiFile2Hash.put(psiFile, newHash);
    // fixme debug only
    DCLogger.info(
        "Hash check (if file been changed) for "
            + psiFile.getName()
            + "\noldHash = "
            + oldHash
            + "\nnewHash = "
            + newHash);

    return !newHash.equals(oldHash);
  }

  private static String getHash(@NotNull PsiFile psiFile) {
    return mapPsiFile2Hash.computeIfAbsent(psiFile, AnalysisData::doGetHash);
  }

  private static String doGetHash(@NotNull PsiFile psiFile) {
    return doGetHash(getFileContent(psiFile));
  }

  private static String doGetHash(@NotNull String fileText) {
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    byte[] encodedHash = messageDigest.digest(fileText.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(encodedHash);
  }

  @NotNull
  private static String getFileContent(@NotNull PsiFile psiFile) {
    // potential OutOfMemoryException for too large projects
    return mapPsiFile2Content.computeIfAbsent(psiFile, AnalysisData::doGetFileContent);
  }

  private static String doGetFileContent(@NotNull PsiFile psiFile) {
    // psiFile.getText() is NOT expensive as it's goes to VirtualFileContent.getText()
    return DeepCodeUtils.computeInReadActionInSmartMode(psiFile.getProject(), psiFile::getText);
    /*
        try {
          return new String(Files.readAllBytes(Paths.get(getPath(psiFile))), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
    */
  }

  private static void uploadFiles(
      @NotNull Project project,
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progress) {
    List<FileHash2ContentRequest> listHash2Content = new ArrayList<>(psiFiles.size());
    info("Uploading " + psiFiles.size() + " files... ");
    for (PsiFile psiFile : psiFiles) {
      listHash2Content.add(new FileHash2ContentRequest(getHash(psiFile), getFileContent(psiFile)));
      //      logDeepCode("Uploading file: " + getPath(psiFile));
    }
    if (listHash2Content.isEmpty()) return;

    EmptyResponse uploadFilesResponse =
        DeepCodeRestApi.UploadFiles(DeepCodeParams.getSessionToken(), bundleId, listHash2Content);
    isNotSucceed(project, uploadFilesResponse, "Bad UploadFiles request: ");
  }

  @NotNull
  private static GetAnalysisResponse doRetrieveSuggestions(
      @NotNull Project project,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progressIndicator) {
    GetAnalysisResponse response;
    int counter = 0;
    do {
      if (counter > 0) DeepCodeUtils.delay(1000);
      response =
          DeepCodeRestApi.getAnalysis(
              DeepCodeParams.getSessionToken(),
              bundleId,
              DeepCodeParams.getMinSeverity(),
              DeepCodeParams.useLinter());

      info(response.toString());
      if (isNotSucceed(project, response, "Bad GetAnalysis request: "))
        return new GetAnalysisResponse();
      ProgressManager.checkCanceled();
      double progress = response.getProgress();
      if (progress == 0) progress = ((double) counter) / 100;
      progressIndicator.setFraction(progress);
      progressIndicator.setText(WAITING_FOR_ANALYSIS_TEXT + (int) (progress * 100) + "% done");
      // fixme
      if (counter == 100) break;
      counter++;
    } while (!response.getStatus().equals("DONE"));
    return response;
  }

  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> parseGetAnalysisResponse(
      @NotNull Collection<PsiFile> psiFiles,
      GetAnalysisResponse response,
      @NotNull ProgressIndicator progressIndicator) {
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    if (!response.getStatus().equals("DONE")) return EMPTY_MAP;
    AnalysisResults analysisResults = response.getAnalysisResults();
    analysisUrl = response.getAnalysisURL();
    if (analysisResults == null) {
      LOG.error("AnalysisResults is null for: {}", response);
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
        LOG.error("Suggestions is empty for: {}", response);
        return EMPTY_MAP;
      }
      ProgressManager.checkCanceled();
      // fixme debug only
      // DCLogger.info("parseGetAnalysisResponse before Document requested");
      Document document =
          DeepCodeUtils.computeInReadActionInSmartMode(
              psiFile.getProject(), psiFile.getViewProvider()::getDocument);
      // fixme debug only
      // DCLogger.info("parseGetAnalysisResponse after Document requested");
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
    return new FileContent(DeepCodeUtils.getDeepCodedFilePath(psiFile), psiFile.getText());
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

  public static void clearCache(@Nullable final Project project) {
    info("Cache clearance requested for project: " + project);
    mapPsiFile2Hash.clear();
    mapPsiFile2Content.clear();
    if (project == null) {
      try {
        MUTEX.lock();
        mapFile2Suggestions.clear();
      } finally {
        MUTEX.unlock();
      }
      mapProject2BundleId.clear();
      for (Project prj : ProjectManager.getInstance().getOpenProjects()) {
        ServiceManager.getService(prj, myTodoView.class).refresh();
      }
    } else {
      removeProjectFromCache(project);
      ServiceManager.getService(project, myTodoView.class).refresh();
    }
    analysisUrl = "";
  }
}
