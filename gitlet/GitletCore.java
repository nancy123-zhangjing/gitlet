package gitlet;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.*;

import static gitlet.Repository.*;
import static gitlet.Utils.*;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Basic tool class for Gitlet.
 *
 * @author 张婧
 */
public class GitletCore {
    public static Commit getHeadCommit() {
        String headContent = readContentsAsString(HEAD_DIR);
        String branchFilePath = headContent.split(" ")[1]; //从内容中分离出路径
        File branchFile = join(GITLET_DIR, branchFilePath);
        String commitHash = readContentsAsString(branchFile);
        File commitFile = join(COMMIT_DIR, commitHash);
        return readObject(commitFile, Commit.class);
        //注意这里返回的并不是原来的对象，而是一个和原来对象的字段值相同的全新的对象
    }
    public static String getHeadCommitHash() {
        String headContent = readContentsAsString(HEAD_DIR);
        String branchFilePath = headContent.split(" ")[1]; //从内容中分离出路径
        File branchFile = join(GITLET_DIR, branchFilePath);
        return readContentsAsString(branchFile);
    }
    public static Commit getWantedHeadCommit(String branchName) {
        File branchFile = new File(BRANCH_DIR, branchName);
        String commitHash = readContentsAsString(branchFile);
        File commitFile = join(COMMIT_DIR, commitHash);
        return readObject(commitFile, Commit.class);
    }
    public static Commit getRemoteWantedHeadCommit(String branchName, File repoFile) {
        File branchFile = join(repoFile, "refs", "heads", branchName);
        String commitHash = readContentsAsString(branchFile);
        File commitFile = join(repoFile, "objects", "commit", commitHash);
        return readObject(commitFile, Commit.class);
    }
    public static Commit getFirstParentCommit(Commit currentCommit) {
        if (currentCommit.getFirstParent() == null) {
            return null;
        }
        File parentCommitFile = join(COMMIT_DIR, currentCommit.getFirstParent());
        return readObject(parentCommitFile, Commit.class);
    }
    public static String getCurrentBranchName() {
        String headContent = readContentsAsString(HEAD_DIR);
        String prefix = "ref: refs/heads/";

        // 从前缀之后的位置开始截取，直到字符串末尾
        //这是为了处理远程分支的情况
        return headContent.substring(prefix.length());
    }
    public static void writeObjectToFile(Commit commit, StagingArea stagingArea) {
        String commithash = getHashOf(commit);
        File newCommitFile = Utils.join(COMMIT_DIR, commithash);
        //注意我们要动态获取当前的分支名
        File currentbranch = Utils.join(BRANCH_DIR, getCurrentBranchName());
        writeObject(newCommitFile, commit);
        writeContents(currentbranch, commithash);
        stagingArea.saveToFile();
    }
    public static String getFullCommitHash(String shortCommitHash) {
        //通过缩写hash得到完整hash，如果不存在，则返回空
        List<String> allCommitHash = plainFilenamesIn(COMMIT_DIR);
        for (String fullHash : allCommitHash) {
            if (fullHash.startsWith(shortCommitHash)) {
                return fullHash;
            }
        }
        return null;
    }
    //给一个commit,如果他是头结点，返回其所在的分支名，否则返回null
    public static String isHeadCommit(Commit commit) {
        List<String> allBranchName = plainFilenamesIn(BRANCH_DIR);
        Map<String, String> nameHashMap = new HashMap<>();
        for (String branchName : allBranchName) {
            File branchPath = join(BRANCH_DIR, branchName);
            String headCommitHash = readContentsAsString(branchPath);
            nameHashMap.put(headCommitHash, branchName);
        }
        String commitHash = getHashOf(commit);
        if (nameHashMap.containsKey(commitHash)) {
            return nameHashMap.get(commitHash);
        }
        return null;
    }
    public static File getAndValidateRemotePath(String remoteName) {
        // 1. 检查远程配置文件是否存在且是一个文件。
        //    如果不存在，我们无法找到远程目录。
        File remoteConfigFile = join(REMOTE_PATH_DIR, remoteName);
        if (!remoteConfigFile.isFile()) {
            exitWithError("Remote directory not found.");
        }
        String rawPath = readContentsAsString(remoteConfigFile);

        try {
            // 2. 尝试将字符串路径解析为真实的、规范化的路径。
            //    toRealPath() 会检查路径是否存在，如果不存在会抛出 IOException。
            Path canonicalPath = Paths.get(rawPath).toRealPath();

            // 3. 检查解析出的路径是否是一个目录。
            if (Files.isDirectory(canonicalPath)) {
                return canonicalPath.toFile();
            }
        } catch (IOException | InvalidPathException e) {
            // - IOException: 路径不存在于文件系统中。
            // - InvalidPathException: 路径字符串的格式是非法的。
        }
        exitWithError("Remote directory not found.");
        return null;
    }
    public static Commit loadCommitByHash(String hash) {
        File commitFile = join(COMMIT_DIR, hash);
        return readObject(commitFile, Commit.class);
    }

    public static Commit loadCommitByHashRemote(String hash, File remoteDir) {
        File commitFile = join(remoteDir, "objects", "commit", hash);
        return readObject(commitFile, Commit.class);
    }

    public static String getHashOf(Commit commit) {
        return sha1(serialize(commit));
    }

    public static String getFileContent(String fileHash) {
        if (fileHash == null) {
            return "";
        }
        File filePath = join(BLOB_DIR, fileHash);
        return readContentsAsString(filePath);
    }

    public static String createConflictContent(String contentCurrent, String contentGiven) {

        StringBuilder conflictContent = new StringBuilder();
        String nl = System.lineSeparator();
        conflictContent.append("<<<<<<< HEAD" + nl);
        conflictContent.append(contentCurrent);
        conflictContent.append("=======" + nl);
        conflictContent.append(contentGiven);
        conflictContent.append(">>>>>>>" + nl);
        return conflictContent.toString();
    }
}
