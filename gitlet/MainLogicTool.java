package gitlet;

import java.io.File;
import java.util.*;
import java.util.Objects;

import static gitlet.Repository.*;
import static gitlet.Utils.*;
import static gitlet.GitletCore.*;

import java.nio.file.Files;
import java.io.IOException;

/**
 * Tool class for Gitlet.
 *
 * @author 张婧
 */
public class MainLogicTool {
    public static void initTool() {
        Commit initialcommit = new Commit();
        StagingArea initialStagingArea = new StagingArea();
        byte[] initialcommitbyte = serialize(initialcommit);
        String initialcommithash = sha1(initialcommitbyte);
        File initial = Utils.join(COMMIT_DIR, initialcommithash);
        File masterbranch = Utils.join(BRANCH_DIR, "master");
        writeObject(initial, initialcommit);
        writeContents(masterbranch, initialcommithash);
        writeContents(HEAD_DIR, "ref: refs/heads/master");
        initialStagingArea.saveToFile();
    }
    
    public static void printCommit(Commit commit, String hashcodeOfCommit) {
        System.out.println("===");
        System.out.println("commit " + hashcodeOfCommit);
        System.out.println("Date: " + commit.getTimestamp());
        System.out.println(commit.getMessage());
        System.out.println();
    }

    public static void printStatus(String currentBranchName, List<String> allBranchName,
                                   Map<String, String> fileToAdd, Set<String> fileToRemove) {
        System.out.println("=== Branches ===");
        List<String> branchNames = new ArrayList<>();
        for (String branchName : allBranchName) {
            branchNames.add(branchName);
        } Collections.sort(branchNames);
        for (String branchName : branchNames) {
            if (branchName.equals(currentBranchName)) {
                System.out.println("*" + branchName);
            } else {
                System.out.println(branchName);
            }
        } System.out.println();
        System.out.println("=== Staged Files ===");
        List<String> addedFileNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileToAdd.entrySet()) {
            addedFileNames.add(entry.getKey());
        } Collections.sort(addedFileNames);
        for (String addedFileName : addedFileNames) {
            System.out.println(addedFileName);
        } System.out.println();
        System.out.println("=== Removed Files ===");
        List<String> removedFileNames = new ArrayList<>();
        for (String filename : fileToRemove) {
            removedFileNames.add(filename);
        } Collections.sort(removedFileNames);
        for (String removedFileName : removedFileNames) {
            System.out.println(removedFileName);
        } System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        Map<String, String> trackedFile = getHeadCommit().getTrackedFileCopy();
        Map<String, String> notStagedFile = new TreeMap<>(); //TreeMap会自动根据文件名进行字典排序
        for (Map.Entry<String, String> entry : trackedFile.entrySet()) { //对于commit中跟踪的每一个文件
            File cwdFilePath = join(CWD, entry.getKey());
            if (cwdFilePath.exists()) { //如果该文件存在于工作区
                byte[] fileInByte = readContents(cwdFilePath); //得到当前工作区的文件的byte值
                String hashcodeOfFile = sha1(fileInByte); //用byte值生成当前工作区该文件的hash码
                if (!entry.getValue().equals(hashcodeOfFile) 
                        && !fileToAdd.containsKey(entry.getKey())
                        && !fileToRemove.contains(entry.getKey())) { 
                    notStagedFile.put(entry.getKey(), " (modified)"); 
                }
            } else { //如果不存在于工作区
                if (!fileToRemove.contains(entry.getKey())) { 
                    notStagedFile.put(entry.getKey(), " (deleted)");
                }
            }
        }
        for (Map.Entry<String, String> entry : fileToAdd.entrySet()) {
            File cwdFilePath = join(CWD, entry.getKey());
            if (cwdFilePath.exists()) { //如果该文件存在于工作区
                byte[] fileInByte = readContents(cwdFilePath); //得到当前工作区的文件的byte值
                String hashcodeOfFile = sha1(fileInByte); //用byte值生成当前工作区该文件的hash码
                if (!entry.getValue().equals(hashcodeOfFile)) { //工作区文件与add的版本不同
                    notStagedFile.put(entry.getKey(), " (modified)");
                }
            }
            if (!cwdFilePath.exists()) { 
                notStagedFile.put(entry.getKey(), " (deleted)");
            }
        }
        for (Map.Entry<String, String> entry : notStagedFile.entrySet()) {
            System.out.println(entry.getKey() + entry.getValue());
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        List<String> allCwdFiles = Utils.plainFilenamesIn(CWD);
        List<String> cwdFileNames = new ArrayList<>();
        for (String cwdFile : allCwdFiles) {
            if (!fileToAdd.containsKey(cwdFile) && !trackedFile.containsKey(cwdFile) 
                    || fileToRemove.contains(cwdFile)) {
                cwdFileNames.add(cwdFile);
            }
        }
        Collections.sort(cwdFileNames);
        for (String cwdFileName : cwdFileNames) {
            System.out.println(cwdFileName);
        } System.out.println();
    }
    
    public static void checkoutWantedCommitFile(Commit givenCommit, String fileName) {
        /*首先看file_name在tracked_file中是否存在，如果存在就用commit中的版本覆盖
           关于覆盖
           首先，获取commit对应文件版本的hash值，然后从blob中读取该文件的内容
           然后把该文件的内容写入工作区的同名文件中
            如果file_name在tracked_file中不存在，打印错误信息
         */
        Map<String, String> trackedFile = givenCommit.getTrackedFileCopy();
        if (!trackedFile.containsKey(fileName)) {
            exitWithError("File does not exist in that commit.");
        } else {
            String commitFileHash = trackedFile.get(fileName);
            File commitFilePath = join(BLOB_DIR, commitFileHash);
            String commitFileContent = readContentsAsString(commitFilePath);
            File cwdFilePath = join(CWD, fileName);
            writeContents(cwdFilePath, commitFileContent);
        }
    }

    public static void checkoutWantedCommit(Commit wantedCommit) {
        Commit currentCommit = getHeadCommit();
        Map<String, String> currentTrackedFile = currentCommit.getTrackedFileCopy();
        Map<String, String> wantedTrackedFile = wantedCommit.getTrackedFileCopy();
        /*首先你要明白他的思想，即要保证你写过的所有文件（包括Untracked files)
           不会因为这个命令而莫名其妙的消失
           现在已经得到了想要的哪个commit,需要用commit中的文件覆盖当前工作区的文件
            首先要进行安全检查，先看untracked_files的列表（前面的status好像计算过）
            遍历untracked_files,只要发现签出commit中也存在同名文件，打印并退出
            安全检查之后，遍历该commit的tracked_files,全部写入工作区
            然后再遍历工作区所有文件，如果该文件不被目标commit跟踪且被当前commit跟踪，则删除
            然后更新HEAD指针
         */
        List<String> allCwdFiles = Utils.plainFilenamesIn(CWD);
        List<String> untrackedFileNames = new ArrayList<>();
        StagingArea currentStagingArea = StagingArea.loadFromFile();
        for (String cwdFile : allCwdFiles) {
            if (!currentTrackedFile.containsKey(cwdFile)
                    && !currentStagingArea.getFileToAdd().containsKey(cwdFile) 
                    || currentStagingArea.getFileToRemove().contains(cwdFile)) {
                untrackedFileNames.add(cwdFile);
            }
        }
        for (String untrackedFileName : untrackedFileNames) { //安全检查
            if (wantedTrackedFile.containsKey(untrackedFileName)) {
                exitWithError("There is an untracked file in the way;" 
                        + " delete it, or add and commit it first.");
            }
        } //安全检查部分
        /*所有在当前分支中跟踪但在签出分支中不存在的文件都将被删除。*/
        for (Map.Entry<String, String> entry : wantedTrackedFile.entrySet()) { //把目标commit的文件写入工作区
            String fileHash = entry.getValue();
            File filePath = join(BLOB_DIR, fileHash);
            String fileContent = readContentsAsString(filePath);
            File cwdFilePath = join(CWD, entry.getKey());
            writeContents(cwdFilePath, fileContent);
        }
        for (String fileInCurrentCommit : currentTrackedFile.keySet()) {
            if (!wantedTrackedFile.containsKey(fileInCurrentCommit)) {
                File fileToDelete = join(CWD, fileInCurrentCommit);
                restrictedDelete(fileToDelete);
            }
        }
    }

    public static Set<String> getParentHashSet(Commit currentCommit) {
        Set<String> currentCommitHashSet = new TreeSet<>();
        Queue<Commit> commitQueue = new LinkedList<>(); //创建一个队列来逐层处理祖先commit
        String currentCommitHash = getHashOf(currentCommit);
        currentCommitHashSet.add(currentCommitHash);
        commitQueue.add(currentCommit); //把当前commit加入待处理列表
        while (!commitQueue.isEmpty()) { //只要还有commit没处理完
            Commit commitToHandle = commitQueue.poll(); //取出一个待处理的commit
            List<String> parentHashes = commitToHandle.getParentList(); //得到父节点列表
            if (!parentHashes.isEmpty()) { //停止条件是到了initial Commit
                for (String parentHash : parentHashes) {
                    if (!parentHashes.isEmpty() && !currentCommitHashSet.contains(parentHash)) {
                        //第二个判断是为了防止把某个节点重复加入队列
                        currentCommitHashSet.add(parentHash); //把所有父节点hash加入祖先列表
                        commitQueue.add(loadCommitByHash(parentHash)); //把父节点加入待处理列表
                    }
                }
            } //现在commit_hash_set里面包含了当前分支的所有可能的父节点的hash值
        }
        return currentCommitHashSet;
    }

    public static Queue<Commit> getParentQueueBetween(Commit currentCommit, Commit remoteCommit) {
        //目的是得到当前commit比remoteCommit多出的那些Commit(此时的迁移已经是push可以顺利进行)
        Set<String> currentCommitHashSet = new HashSet<>();
        Queue<Commit> parentQueueToAdd = new LinkedList<>();
        Queue<Commit> commitQueue = new LinkedList<>(); //创建一个队列来逐层处理祖先commit
        String currentCommitHash = getHashOf(currentCommit);
        String remoteCommitHash = getHashOf(remoteCommit);
        parentQueueToAdd.add(currentCommit);
        currentCommitHashSet.add(currentCommitHash);
        commitQueue.add(currentCommit); //把当前commit加入待处理列表
        while (!commitQueue.isEmpty()) { //只要还有commit没处理完
            Commit commitToHandle = commitQueue.poll(); //取出一个待处理的commit
            List<String> parentHashes = commitToHandle.getParentList(); //得到父节点列表
            String commitToHandleHash = getHashOf(commitToHandle);
            if (!commitToHandleHash.equals(remoteCommitHash)) { 
                //只要还没到那个remoteCommit，也就是说那个remoteCommit是不包含在返回队列里面的
                for (String parentHash : parentHashes) {
                    if (!parentHashes.isEmpty() && !currentCommitHashSet.contains(parentHash)) {
                        //第二个判断是为了防止把某个节点重复加入队列
                        currentCommitHashSet.add(parentHash); //把所有父节点hash加入祖先列表
                        commitQueue.add(loadCommitByHash(parentHash)); //把父节点加入待处理列表
                        parentQueueToAdd.add(loadCommitByHash(parentHash));
                    }
                }
            } //现在commit_hash_set里面包含了当前分支的所有可能的父节点的hash值
        }
        return parentQueueToAdd;
    }
    public static Queue<Commit> getFullParentQueue(Commit currentCommit, File repo) {
        boolean isLocal;
        try {
            isLocal = Files.isSameFile(GITLET_DIR.toPath(), repo.toPath());
        } catch (IOException e) {
            isLocal = false;
        }
        Set<String> currentCommitHashSet = new HashSet<>();
        Queue<Commit> parentQueueToAdd = new LinkedList<>();
        Queue<Commit> commitQueue = new LinkedList<>(); //创建一个队列来逐层处理祖先commit
        String currentCommitHash = getHashOf(currentCommit);
        parentQueueToAdd.add(currentCommit);
        currentCommitHashSet.add(currentCommitHash);
        commitQueue.add(currentCommit); //把当前commit加入待处理列表
        while (!commitQueue.isEmpty()) { //只要还有commit没处理完
            Commit commitToHandle = commitQueue.poll(); //取出一个待处理的commit
            List<String> parentHashes = commitToHandle.getParentList(); //得到父节点列表
            for (String parentHash : parentHashes) {
                if (!parentHashes.isEmpty() && !currentCommitHashSet.contains(parentHash)) { 
                    //第二个判断是为了防止把某个节点重复加入队列
                    currentCommitHashSet.add(parentHash); //把所有父节点hash加入祖先列表
                    if (isLocal) { //在本地找父节点的情况
                        commitQueue.add(loadCommitByHash(parentHash)); //把父节点加入待处理列表
                        parentQueueToAdd.add(loadCommitByHash(parentHash));
                    } else { //在远程找父节点
                        Commit parentCommit = loadCommitByHashRemote(parentHash, repo);
                        commitQueue.add(parentCommit);
                        parentQueueToAdd.add(parentCommit);
                    }

                }
            }
        }
        return parentQueueToAdd;
    }

    //这里是广度优先算法的思想，着重运用了queue的先进先出特性
    public static Commit findSplitPoint(Commit currentCommit, String otherBranchName) {
        Set<String> currentCommitHashSet = getParentHashSet(currentCommit);
        Queue<Commit> commitQueue = new LinkedList<>(); //创建一个队列来逐层处理祖先commit
        Commit otherBranchCommit = getWantedHeadCommit(otherBranchName);
        commitQueue.add(otherBranchCommit);
        Set<String> givenCommitHashSet = new HashSet<>();
        String givenCommitHash = getHashOf(otherBranchCommit);
        givenCommitHashSet.add(givenCommitHash);
        while (!commitQueue.isEmpty()) {
            Commit commitToHandle = commitQueue.poll(); //取出一个待处理的commit
            String otherBranchCommitHash = getHashOf(commitToHandle); //得到这个commit的hash值
            List<String> parentHashes = commitToHandle.getParentList(); //得到父节点列表
            if (currentCommitHashSet.contains(otherBranchCommitHash)) {
                return commitToHandle;
            }
            if (!parentHashes.isEmpty()) {
                for (String parentHash : parentHashes) {
                    if (!parentHashes.isEmpty() && !givenCommitHashSet.contains(parentHash)) {
                        // 要特别注意这里，要避免某个节点的重复访问（因为两个commit可能有同一个parent)
                        givenCommitHashSet.add(parentHash);
                        commitQueue.add(loadCommitByHash(parentHash)); //把父节点加入待处理列表
                    }
                }
            }
        }
        return null;
    }

    public static void haveUntrackedFiles(Commit commit, Commit mergeCommit) {
        List<String> allCwdFiles = Utils.plainFilenamesIn(CWD);
        List<String> untrackedFileNames = new ArrayList<>();
        StagingArea stagingArea = StagingArea.loadFromFile();
        Map<String, String> fileToAdd = stagingArea.getFileToAdd();
        Set<String> fileToRemove = stagingArea.getFileToRemove();
        Map<String, String> trackedFile = commit.getTrackedFileCopy();
        Map<String, String> mergeTrackedFile = mergeCommit.getTrackedFileCopy();
        for (String cwdFile : allCwdFiles) {
            if (!fileToAdd.containsKey(cwdFile) && !trackedFile.containsKey(cwdFile)
                    || fileToRemove.contains(cwdFile)) {
                untrackedFileNames.add(cwdFile);
            }
        }
        for (String untrackedFileName : untrackedFileNames) { //安全检查
            if (mergeTrackedFile.containsKey(untrackedFileName)) {
                exitWithError("There is an untracked file in the way; " 
                       + "delete it, or add and commit it first.");
            }
        }
    }
    

    public static void mergeCommit(Commit splitPoint, Commit currentCommit, 
                                   Commit givenBranchHeadCommit, String otherBranchName) {
        Map<String, String> trackedFiles1 = splitPoint.getTrackedFileCopy();
        Map<String, String> trackedFiles2 = currentCommit.getTrackedFileCopy();
        Map<String, String> trackedFiles3 = givenBranchHeadCommit.getTrackedFileCopy();
        Map<String, String> newTrackedFile = new TreeMap<>(); 
        //用来记录在merge之后的那个commit里面要跟踪哪些文件
        Set<String> fileNameUnion = new HashSet<>();
        fileNameUnion.addAll(trackedFiles1.keySet());
        fileNameUnion.addAll(trackedFiles2.keySet());
        fileNameUnion.addAll(trackedFiles3.keySet());
        boolean confilctOccured = false;
        for (String singleFile : fileNameUnion) {
            boolean haveIn1 = trackedFiles1.containsKey(singleFile);
            boolean haveIn2 = trackedFiles2.containsKey(singleFile);
            boolean haveIn3 = trackedFiles3.containsKey(singleFile);
            String hashIn1 = trackedFiles1.get(singleFile);
            String hashIn2 = trackedFiles2.get(singleFile);
            String hashIn3 = trackedFiles3.get(singleFile);
            if (haveIn1 && haveIn2 && haveIn3) {
                if (Objects.equals(hashIn1, hashIn2) && !Objects.equals(hashIn2, hashIn3)) {
                    newTrackedFile.put(singleFile, hashIn3);
                    String fileContent = getFileContent(hashIn3);
                    File fileInCwd = join(CWD, singleFile);
                    writeContents(fileInCwd, fileContent);
                }
                if (Objects.equals(hashIn1, hashIn3) && !Objects.equals(hashIn3, hashIn2)) {
                    newTrackedFile.put(singleFile, hashIn2);
                }
                if (Objects.equals(hashIn2, hashIn3) && haveIn2) { //修改相同，未被删除
                    newTrackedFile.put(singleFile, hashIn2);
                }
                if (Objects.equals(hashIn2, hashIn3) && !haveIn2) { //修改相同，被删除
                    continue;
                }
            }
            if (!haveIn1 && haveIn2 && !haveIn3) {
                newTrackedFile.put(singleFile, hashIn2);
            }
            if (!haveIn1 && !haveIn2 && haveIn3) {
                newTrackedFile.put(singleFile, hashIn3);
                checkoutWantedCommitFile(givenBranchHeadCommit, singleFile);
            }
            if (Objects.equals(hashIn1, hashIn2) && !haveIn3) {
                File filePath = join(CWD, singleFile);
                restrictedDelete(filePath);
            }
            if (Objects.equals(hashIn1, hashIn3) && !haveIn2) {
                continue;
            }
            if (!Objects.equals(hashIn1, hashIn2) && !Objects.equals(hashIn1, hashIn3) 
                    && !Objects.equals(hashIn2, hashIn3)) {
                confilctOccured = true;
                File filePathCwd = join(CWD, singleFile);
                String contentInCurrentCommit = getFileContent(hashIn2);
                String contentInGivenCommit = getFileContent(hashIn3);
                String newContent = createConflictContent
                        (contentInCurrentCommit, contentInGivenCommit);
                writeContents(filePathCwd, newContent);
                byte[] newFileInByte = readContents(filePathCwd);
                String hashCodeOfNewFile = sha1(newFileInByte);
                newTrackedFile.put(singleFile, hashCodeOfNewFile);
            }

        }
        String message = "Merged " + otherBranchName + " into " + getCurrentBranchName() + ".";
        String currentCommitHash = getHashOf(currentCommit);
        String givenBranchHeadCommitHash = getHashOf(givenBranchHeadCommit);
        createMergeCommit(message, currentCommitHash, givenBranchHeadCommitHash, newTrackedFile);
        //应该直接把我前面判断好的newtrackedfile传入
        if (confilctOccured) {
            System.out.println("Encountered a merge conflict.");
        }
    }
    private static void saveObjectsHelper(Queue<Commit> parentQueueToAdd, File initialBlobListDir,
                                          File blobSourceDir, File blobDestDir, File commitDestDir) {
        
        Queue<Commit> parentQueueToAddCopy = new LinkedList<>(parentQueueToAdd);
        List<String> listIncludeAllBlob = new ArrayList<>(plainFilenamesIn(initialBlobListDir));
        Map<String, String> blobToAdd = new TreeMap<>();
        while (!parentQueueToAdd.isEmpty()) {
            Commit commitToHandle = parentQueueToAdd.poll();
            Map<String, String> trackedFileCopy = commitToHandle.getTrackedFileCopy();
            for (Map.Entry<String, String> entry : trackedFileCopy.entrySet()) {
                if (!listIncludeAllBlob.contains(entry.getValue())) { //这个版本没有被跟踪
                    blobToAdd.put(entry.getKey(), entry.getValue()); //加到需要添加的blob列表
                    listIncludeAllBlob.add(entry.getValue()); //更新已保存的blob列表
                }
            }
        }
        //接下来要把所有的blob保存到那个remote仓库里面去
        for (Map.Entry<String, String> entry : blobToAdd.entrySet()) {
            File blobFile = join(blobSourceDir, entry.getValue());
            byte[] blobInByte = readContents(blobFile);
            File blobFileNew = join(blobDestDir, entry.getValue());
            writeContents(blobFileNew, blobInByte);
        }
        //然后保存Commit
        while (!parentQueueToAddCopy.isEmpty()) {
            Commit commitToSave = parentQueueToAddCopy.poll();
            String commitToSaveHash = getHashOf(commitToSave);
            //得到这个Commit的新的储存地址
            File commitToSavePath = join(commitDestDir, commitToSaveHash);
            //把这个Commit保存到新的地址
            writeObject(commitToSavePath, commitToSave);
        }
    }
    public static void saveAllCommitAndBlobPush(Queue<Commit> parentQueueToAdd, File newRepo) {
        File remoteObjectFile = join(newRepo, "objects");
        File remoteBlobsFile = join(remoteObjectFile, "blobs");
        File remoteCommitFile = join(remoteObjectFile, "commit");
        saveObjectsHelper(parentQueueToAdd, remoteBlobsFile, BLOB_DIR, remoteBlobsFile, remoteCommitFile);
    }

    public static void saveAllCommitAndBlobFetch(Queue<Commit> parentQueueToAdd, File remoteRepo) {
        // 【保留】保留你原来的路径定义
        File remoteObjectFile = join(remoteRepo, "objects");
        File remoteBlobsFile = join(remoteObjectFile, "blobs");
        File remoteCommitFile = join(remoteObjectFile, "commit"); 
        saveObjectsHelper(parentQueueToAdd, BLOB_DIR, remoteBlobsFile, BLOB_DIR, COMMIT_DIR);
    }
    
}
