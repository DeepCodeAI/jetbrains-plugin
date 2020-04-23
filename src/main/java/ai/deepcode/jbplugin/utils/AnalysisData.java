package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.ExtendBundleRequest;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileHash2ContentRequest;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.responses.*;
import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.ui.utils.DeepCodeUIUtils;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static ai.deepcode.jbplugin.utils.DeepCodeUtils.logDeepCode;

public final class AnalysisData {
  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalysisData");
  private static final Map<PsiFile, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();

  private AnalysisData() {}

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<PsiFile, List<SuggestionForFile>> mapFile2Suggestions =
      new Hashtable<>(); // we need read and write full data lock
  //      new ConcurrentHashMap<>();

  public static class SuggestionForFile {
    private final String id;
    private final String message;
    private final int severity;
    private final List<TextRange> ranges;

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

  private static final Map<Project, String> mapProject2BundleId = new ConcurrentHashMap<>();

  /** Add File Listener to clear caches for file if it was changed. */
  private static void addFileListener(@NotNull final Project project) {
    if (!mapProject2BundleId.containsKey(project)) {
      PsiManager.getInstance(project)
          .addPsiTreeChangeListener(
              new PsiTreeChangeAdapter() {
                @Override
                public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
                  PsiFile file = event.getFile();
                  if (file != null && mapFile2Suggestions.remove(file) != null) {
                    logDeepCode("Removed from cache: " + file);
                  }
                }
              });
      mapProject2BundleId.put(project, "");
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
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    psiFiles.stream().map(PsiElement::getProject).distinct().forEach(AnalysisData::addFileListener);
    Collection<PsiFile> filesToProcced = new HashSet<>();
    for (PsiFile file : psiFiles) {
      if (!mapFile2Suggestions.containsKey(file)) {
        filesToProcced.add(file);
      }
    }
    if (!filesToProcced.isEmpty()) {
      logDeepCode("Analysis requested for " + psiFiles.size() + " files: " + psiFiles.toString());
      logDeepCode("Files to proceed (not found in cache): " + filesToProcced.size());
    }

    mapFile2Suggestions.putAll(retrieveSuggestions(filesToProcced));

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
      logDeepCode(
          "Suggestions not found for " + brokenKeys.size() + " files: " + brokenKeys.toString());
    }
    return result;
  }

  private static boolean isNotSucceed(EmptyResponse response, String message) {
    if (response.getStatusCode() == 200) return false;
    // new logging was not requested during current session.
    if ((response.getStatusCode() == 401) && !DeepCodeParams.loggingRequested) {
      DeepCodeUtils.requestNewLogin(null);
    }
    logDeepCode(message + response.getStatusCode() + " " + response.getStatusDescription());
    return true;
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes
  private static final Map<PsiFile, String> mapPsiFile2Hash = new HashMap<>();
  private static final Map<PsiFile, String> mapPsiFile2Content = new HashMap<>();

  /** Perform costly network request. <b>No cache checks!</b> */
  @NotNull
  private static Map<PsiFile, List<SuggestionForFile>> retrieveSuggestions(
      @NotNull Collection<PsiFile> psiFiles) {
    if (psiFiles.isEmpty() || DeepCodeUtils.isNotLogged(null)) return Collections.emptyMap();
    Map<PsiFile, List<SuggestionForFile>> result = new HashMap<>();
    ProgressIndicator progress = new StatusBarProgress();
    //    progress.setIndeterminate(false);
    progress.start();

    long startTime;
    // Create Bundle
    startTime = System.currentTimeMillis();
    ProgressManager.checkCanceled();
    progress.setText("Preparing files for upload...");
    mapPsiFile2Hash.clear();
    mapPsiFile2Content.clear();
    Project project = psiFiles.stream().findFirst().get().getProject();
    Map<String, String> mapPath2Hash = new HashMap<>();
    long sizePath2Hash = 0;
    for (PsiFile file : psiFiles) {
      final String path = getDeepCodedFilePath(file);
      final String hash = getHash(file);
      mapPath2Hash.put(path, hash);
      sizePath2Hash += (path.length() + hash.length()) * 2; // rough estimation of bytes occupied
      if (sizePath2Hash > MAX_BUNDLE_SIZE) {
        CreateBundleResponse tempBundleResponse = doGetBundleResponse(project, mapPath2Hash);
        if (isNotSucceed(tempBundleResponse, "Bad Create/Extend Bundle request: "))
          return EMPTY_MAP;
        sizePath2Hash = 0;
        mapPath2Hash.clear();
      }
    }
    CreateBundleResponse createBundleResponse = doGetBundleResponse(project, mapPath2Hash);
    if (isNotSucceed(createBundleResponse, "Bad Create/Extend Bundle request: ")) return EMPTY_MAP;
    logDeepCode(
        "--- Create/Extend Bundle took: "
            + (System.currentTimeMillis() - startTime)
            + " milliseconds");

    final String bundleId = createBundleResponse.getBundleId();
    logDeepCode("bundleId: " + bundleId);

    final List<String> missingFiles = createBundleResponse.getMissingFiles();
    logDeepCode("missingFiles: " + missingFiles.size());

    // Upload Files
    startTime = System.currentTimeMillis();
    ProgressManager.checkCanceled();
    progress.setText("Uploading files to the server...");

    long fileChunkSize = 0;
    List<PsiFile> filesChunk = new ArrayList<>();
    for (String filePath : missingFiles) {
      PsiFile psiFile =
          psiFiles.stream()
              .filter(f -> getDeepCodedFilePath(f).equals(filePath))
              .findAny()
              .orElse(null);
      if (psiFile == null) {
        logDeepCode(
            "File requested in missingFiles not found in psiFiles (skipped to upload): "
                + filePath);
        continue;
      }
      final long fileSize = psiFile.getVirtualFile().getLength();
      if (fileChunkSize + fileSize > MAX_BUNDLE_SIZE) {
        logDeepCode("Files-chunk size: " + fileChunkSize);
        uploadFiles(filesChunk, bundleId, progress);
        fileChunkSize = 0;
        filesChunk.clear();
      }
      fileChunkSize += fileSize;
      filesChunk.add(psiFile);
    }
    logDeepCode("Last files-chunk size: " + fileChunkSize);
    uploadFiles(filesChunk, bundleId, progress);

    mapPsiFile2Hash.clear();
    mapPsiFile2Content.clear();
    logDeepCode(
        "--- Upload Files took: " + (System.currentTimeMillis() - startTime) + " milliseconds");

    // Get Analysis
    startTime = System.currentTimeMillis();
    GetAnalysisResponse getAnalysisResponse = retrieveSuggestions(bundleId, progress);
    result = parseGetAnalysisResponse(psiFiles, getAnalysisResponse);
    logDeepCode(
        "--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    progress.stop();
    return result;
  }

  private static CreateBundleResponse doGetBundleResponse(
      Project project, Map<String, String> mapPath2Hash) {
    final FileHashRequest fileHashRequest = new FileHashRequest(mapPath2Hash);
    final String parentBundleId = mapProject2BundleId.getOrDefault(project, "");
    String message =
        (parentBundleId.isEmpty())
            ? "Creating new Bundle with "
            : "Extending existing Bundle [" + parentBundleId + "] with ";
    logDeepCode(message + mapPath2Hash.size() + " files");
    final CreateBundleResponse bundleResponse =
        (parentBundleId.isEmpty())
            // check if bundleID for the project already been created
            ? DeepCodeRestApi.createBundle(DeepCodeParams.getSessionToken(), fileHashRequest)
            : DeepCodeRestApi.extendBundle(
                DeepCodeParams.getSessionToken(),
                parentBundleId,
                new ExtendBundleRequest(fileHashRequest.getFiles(), Collections.emptyList()));
    mapProject2BundleId.put(project, bundleResponse.getBundleId());
    return bundleResponse;
  }

  private static String getDeepCodedFilePath(PsiFile psiFile) {
    return "/" + getPath(psiFile);
  }

  private static String getPath(PsiFile psiFile) {
    return psiFile.getVirtualFile().getPath(); // .replace('/', '\\');
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

  private static String getHash(PsiFile psiFile) {
    return mapPsiFile2Hash.computeIfAbsent(psiFile, AnalysisData::doGetHash);
  }

  private static String doGetHash(PsiFile psiFile) {
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    String fileText = getFileContent(psiFile);
    byte[] encodedHash = messageDigest.digest(fileText.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(encodedHash);
  }

  @NotNull
  private static String getFileContent(PsiFile psiFile) {
    // potential OutOfMemoryException for too large projects
    return mapPsiFile2Content.computeIfAbsent(psiFile, AnalysisData::doGetFileContent);
  }

  private static String doGetFileContent(PsiFile psiFile) {
    // psiFile.getText() might be too expensive (or not?)
    return psiFile.getText();
    /*
        try {
          return new String(Files.readAllBytes(Paths.get(getPath(psiFile))), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
    */
  }

  private static void uploadFiles(
      @NotNull Collection<PsiFile> psiFiles,
      @NotNull String bundleId,
      @NotNull ProgressIndicator progress) {
    List<FileHash2ContentRequest> listHash2Content = new ArrayList<>(psiFiles.size());
    logDeepCode("Uploading " + psiFiles.size() + " files... ");
    for (PsiFile psiFile : psiFiles) {
      listHash2Content.add(new FileHash2ContentRequest(getHash(psiFile), getFileContent(psiFile)));
      //      logDeepCode("Uploading file: " + getPath(psiFile));
    }
    if (listHash2Content.isEmpty()) return;

    EmptyResponse uploadFilesResponse =
        DeepCodeRestApi.UploadFiles(DeepCodeParams.getSessionToken(), bundleId, listHash2Content);
    isNotSucceed(uploadFilesResponse, "Bad UploadFiles request: ");
  }

  @NotNull
  private static GetAnalysisResponse retrieveSuggestions(
      @NotNull String bundleId, @NotNull ProgressIndicator progress) {
    ProgressManager.checkCanceled();
    progress.setText("Waiting for analysis from server...");
    GetAnalysisResponse response;
    int counter = 0;
    do {
      try {
        if (counter > 0) Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
        Thread.currentThread().interrupt();
      }
      response =
          DeepCodeRestApi.getAnalysis(
              DeepCodeParams.getSessionToken(),
              bundleId,
              DeepCodeParams.getMinSeverity(),
              DeepCodeParams.useLinter());

      //      progress.setFraction(((double) counter) / 10);
      // todo: show progress notification
      logDeepCode(response.toString());
      if (isNotSucceed(response, "Bad GetAnalysis request: ")) return new GetAnalysisResponse();
      ProgressManager.checkCanceled();
      // fixme
      if (counter == 100) break;
      counter++;
    } while (!response.getStatus().equals("DONE"));
    return response;
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

  public static void clearCache(@Nullable final Project project) {
    if (project == null) {
      mapFile2Suggestions.clear();
      mapProject2BundleId.clear();
      for (Project prj : ProjectManager.getInstance().getOpenProjects()) {
        ServiceManager.getService(prj, myTodoView.class).refresh(DeepCodeUIUtils.EMPTY_EWI_ICON);
      }
    } else {
      List<PsiFile> filesToRemove =
          mapFile2Suggestions.keySet().stream()
              .filter(file -> file.getProject().equals(project))
              .collect(Collectors.toList());
      filesToRemove.forEach(mapFile2Suggestions::remove);
      mapProject2BundleId.remove(project);
      ServiceManager.getService(project, myTodoView.class).refresh(DeepCodeUIUtils.EMPTY_EWI_ICON);
    }
  }
}
