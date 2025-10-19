package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Utils.exitWithError;
import static gitlet.MainLogicTool.*;
import static gitlet.StagingArea.*;
import static gitlet.GitletCore.*;
import static gitlet.Utils.plainFilenamesIn;

/**
 * Represents a gitlet repository.
 * does at a high level.
 *
 * @author 张婧
 */
public class Repository {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File COMMIT_DIR = join(GITLET_DIR, "objects", "commit");
    public static final File BRANCH_DIR = join(GITLET_DIR, "refs", "heads");
    public static final File HEAD_DIR = join(GITLET_DIR, "HEAD");
    public static final File STAGING_AREA_FILE = join(GITLET_DIR, "index");
    public static final File BLOB_DIR = join(GITLET_DIR, "objects", "blobs");
    public static final File REMOTE_PATH_DIR = join(GITLET_DIR, "remotes");


    public static void setupPersistence() {
        GITLET_DIR.mkdir(); //创建.gitlet文件夹
        COMMIT_DIR.mkdirs();
        BLOB_DIR.mkdirs();
        BRANCH_DIR.mkdirs(); //一定要注意mkdir()和mkdirs()之间的区别！mkdir()只能创建一层目录！
        REMOTE_PATH_DIR.mkdirs();
    }

    public static void init() {
        /**先判断.gitlet是否已经存在*/
        if (GITLET_DIR.exists()) {
            exitWithError("A Gitlet version-control system" 
                    + " already exists in the current directory.");
        }
        setupPersistence();
        /**调用默认的Commit构造函数来构建一个Commit实例， 
         * 然后把这个对象序列化，然后再根据文件的内容生成hash值，作为文件名称*/
        initTool();
    }

    public static void add(String fileName) { /*记得要存blob*/
        /*将文件当前存在的副本添加到暂存区（参见 commit 命令的说明）。因此，添加文件也称为暂存文件以备添加。
           暂存已暂存的文件会用新内容覆盖暂存区中的先前条目。
           暂存区应位于 .gitlet 中的某个位置。如果文件的当前工作版本
           与当前提交中的版本相同，则不会将其暂存以备添加；如果文件已在暂存区中，则将其从暂存区中移除
           （当文件被更改、添加，然后又改回其原始版本时，可能会发生这种情况）。
           如果在执行命令时该文件处于暂存状态以备删除，则将不再暂存该文件
         */
        /*首先根据名字拼接得到在CWD中的路径，如果不存在，则打印错误信息退出
           如果存在，则先加载暂存区和得到当前commit，
           如果在暂存区的file_to_remove里面存在同名文件，则从file_to_remove中移除,并加到file_to_add里面
           如果commit中存在同名文件且内容相同，则把文件从暂存区移除
           最终把暂存区重新写入文件
         */
        File fileToAdd = join(CWD, fileName);
        if (!fileToAdd.exists()) {
            exitWithError("File does not exist.");
        }
        byte[] fileInByte = readContents(fileToAdd); //得到当前想要add的文件的byte值
        String hashcodeOfFile = sha1(fileInByte); //用byte值生成hash码
        StagingArea stagingArea = StagingArea.loadFromFile();
        Commit currentCommit = getHeadCommit();
        if (stagingArea.getFileToRemove().contains(fileName)) {
            stagingArea.getFileToRemove().remove(fileName);
        }
        Map<String, String> fileset = currentCommit.getTrackedFileCopy();
        if (fileset.containsKey(fileName)) {
            if (fileset.get(fileName).equals(hashcodeOfFile)) {
                stagingArea.getFileToAdd().remove(fileName);
                stagingArea.getFileToRemove().remove(fileName);
                stagingArea.saveToFile();
                return;
            }
        }
        File blobFile = Utils.join(BLOB_DIR, hashcodeOfFile);
        Utils.writeContents(blobFile, fileInByte);
        stagingArea.getFileToAdd().put(fileName, hashcodeOfFile);
        stagingArea.saveToFile();

    }

    public static void commit(String message) {
        /*commit的核心在于先继承其父commit的文件快照，然后根据暂存区来修改*/
        //先获取当前commit
        Commit oldCommit = getHeadCommit();
        String oldCommitHash = getHeadCommitHash();
        Map<String, String> oldTrackedFileCopy = oldCommit.getTrackedFileCopy();
        //把暂存区加载出来
        StagingArea currentStagingArea = loadFromFile();
        //遍历暂存区里面的file_to_add,全部加到commit中的tracked_file里面去
        Map<String, String> fileToAdd = currentStagingArea.getFileToAdd();
        Set<String> fileToRemove = currentStagingArea.getFileToRemove();
        for (Map.Entry<String, String> singleFileToAdd : fileToAdd.entrySet()) {
            oldTrackedFileCopy.put(singleFileToAdd.getKey(), singleFileToAdd.getValue());
        }
        //遍历file_to_remove,从tracked_file里面删除
        for (String singleFileToRemove : fileToRemove) {
            oldTrackedFileCopy.remove(singleFileToRemove);
        }
        //创建一个新的commit
        Commit newCommit = new Commit(oldCommitHash, message, oldTrackedFileCopy);
        //清空暂存区
        currentStagingArea.clear();
        //把两个对象都写进文件里，并更新指针
        writeObjectToFile(newCommit, currentStagingArea);
    }

    public static void createMergeCommit(String message, String firstParentHash, 
                                         String secondParentHash,
                                         Map<String, String> newTrackedFile) {
        /*我知道我哪里错了，我一开始把staging_area当成我想要在新的那个commit里面应该跟踪的文件列表来用的
           所以我应该做的是建一个Map，然后在每个条件判断后面执行某些操作，最后用这个Map来构造我的新的commit!!!!!
         */
        // 使用为 merge 设计的双父节点构造函数
        Commit mergeCommit = new Commit(firstParentHash, secondParentHash, message, newTrackedFile);
        StagingArea stagingArea = StagingArea.loadFromFile();
        stagingArea.clear(); //现在已经得到了全新的commit和staging_area
        writeObjectToFile(mergeCommit, stagingArea); //把commit和staging_area写进文件，并更新指针

    }

    public static void rm(String fileName) {
        //首先去暂存区找，如果文件在暂存区里面，就把它从暂存区移除
        StagingArea stagingArea = StagingArea.loadFromFile(); //加载暂存区
        Map<String, String> fileToAdd = stagingArea.getFileToAdd();
        if (fileToAdd.containsKey(fileName)) {
            fileToAdd.remove(fileName);
            //重新构造Staging_area
            stagingArea.saveToFile();
            return;
        }
        //如果文件不在暂存区，就去看该文件是否在当前commit中被跟踪
        Commit currentCommit = getHeadCommit();
        Map<String, String> currentTrackedFileCopy = currentCommit.getTrackedFileCopy();
        /*如果想要remove的文件在最近一次commit中被跟踪，需要在下一次commit中移除对这个文件的跟踪*/
        if (currentTrackedFileCopy.containsKey(fileName)) {
            stagingArea.getFileToRemove().add(fileName);
            //重构staging_area
            stagingArea.saveToFile();
            //如果该文件仍存在于工作目录中，就移除
            File rmInWorkingDir = join(CWD, fileName);
            if (rmInWorkingDir.exists()) {
                restrictedDelete(rmInWorkingDir);
            }
            return;
        }
        exitWithError("No reason to remove the file.");
    }

    public static void log() {
        Commit currentCommit = getHeadCommit(); //得到一个和原来的commit字段相同的对象
        String commitHash = getHeadCommitHash();
        do {
            System.out.println("===");
            System.out.println("commit " + commitHash);
            List<String> parentHashes = currentCommit.getParentList();
            if (parentHashes.size() > 1) {
                String parent1Short = parentHashes.get(0).substring(0, 7);
                String parent2Short = parentHashes.get(1).substring(0, 7);
                System.out.println("Merge: " + parent1Short + " " + parent2Short);
            }
            System.out.println("Date: " + currentCommit.getTimestamp());
            System.out.println(currentCommit.getMessage());
            System.out.println();
            commitHash = currentCommit.getFirstParent();
            currentCommit = getFirstParentCommit(currentCommit);
        } while (currentCommit != null);
    }

    public static void globalLog() {
        List<String> allCommitFiles = plainFilenamesIn(COMMIT_DIR);
        for (String fileName : allCommitFiles) {
            File commitFile = Utils.join(COMMIT_DIR, fileName);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            printCommit(commit, fileName);
        }
    }

    public static void find(String commitMessage) {
        List<String> allCommitFiles = plainFilenamesIn(COMMIT_DIR);
        boolean flag = false;
        for (String fileName : allCommitFiles) {
            File commitFile = Utils.join(COMMIT_DIR, fileName);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            if (commit.getMessage().equals(commitMessage)) {
                System.out.println(fileName);
                flag = true;
            }
        }
        if (!flag) {
            exitWithError("Found no commit with that message.");
        }
    }

    public static void status() {
        String currentBranchName = getCurrentBranchName();
        List<String> allBranchName = plainFilenamesIn(BRANCH_DIR);
        StagingArea currentStagingArea = StagingArea.loadFromFile();
        Map<String, String> fileToAdd = currentStagingArea.getFileToAdd();
        Set<String> fileToRemove = currentStagingArea.getFileToRemove();
        printStatus(currentBranchName, allBranchName, fileToAdd, fileToRemove);
    }

    public static void checkoutCurrentCommitFile(String fileName) {
        Commit currentCommit = getHeadCommit();
        checkoutWantedCommitFile(currentCommit, fileName);
    }

    public static void checkoutGivenCommitFile(String hashcode, String filename) {
        File commitPath = join(COMMIT_DIR, hashcode);
        if (!commitPath.exists()) {
            exitWithError("No commit with that id exists.");
        }
        Commit givenCommit = readObject(commitPath, Commit.class);
        checkoutWantedCommitFile(givenCommit, filename);
    }

    public static void checkOutBranch(String branchName) {
        File branchFile = new File(BRANCH_DIR, branchName);
        if (!branchFile.isFile()) {
            exitWithError("No such branch exists.");
        }
        String currentBranchName = getCurrentBranchName();
        if (currentBranchName.equals(branchName)) {
            exitWithError("No need to checkout the current branch.");
        }
        Commit wantedCommit = getWantedHeadCommit(branchName);
        checkoutWantedCommit(wantedCommit);
        StagingArea currentStagingArea = StagingArea.loadFromFile();
        currentStagingArea.clear();
        currentStagingArea.saveToFile();
        writeContents(HEAD_DIR, "ref: refs/heads/" + branchName);
    }

    public static void makeBranch(String branchName) {
        File newBranch = join(BRANCH_DIR, branchName);
        if (newBranch.exists()) {
            exitWithError("A branch with that name already exists.");
        }
        String currentCommitHash = getHeadCommitHash();
        writeContents(newBranch, currentCommitHash);
    }

    public static void removeBranch(String branchNameToRemove) {
        File branchFile = join(BRANCH_DIR, branchNameToRemove);
        if (!branchFile.exists()) {
            exitWithError("A branch with that name does not exist.");
        }
        if (branchNameToRemove.equals(getCurrentBranchName())) {
            exitWithError("Cannot remove the current branch.");
        }
        branchFile.delete();
    }

    /*reset需要我们checkout某个commit的全部内容
       现在我已经有一个函数，给branch_name,可以check_out该branch头部commit的全部内容
       只需要稍微修改即可
           */
    public static void resetACommit(String commitId) {
        File commitPath = join(COMMIT_DIR, commitId);
        Commit wantedCommit = readObject(commitPath, Commit.class);
        checkoutWantedCommit(wantedCommit);
        StagingArea currentStagingArea = StagingArea.loadFromFile();
        currentStagingArea.clear();
        currentStagingArea.saveToFile();
        String currentBranch = getCurrentBranchName();
        File currentBranchPath = join(BRANCH_DIR, currentBranch);
        writeContents(currentBranchPath, commitId);
    }

    public static void merge(String branchName) {
        /*在进行真正的合并操作之前，首先需要进行安全检查
           首先是检查暂存区的file_to_add和file_to_rm有没有文件，有则打印"You have uncommitted changes."
           然后再去存放分支的文件夹查找是否存在该分支名，否则打印"A branch with that name does not exist."
           然后要获取当前所在的分支，如果与branch_name相同，则打印"Cannot merge a branch with itself."
           然后检查是否有未跟踪文件（之前在status里面写过),如果有，则打印
           “There is an untracked file in the way; delete it, or add and commit it first.”
           安全检查完成之后，我们先要找到分裂点（包装成函数）find_split_point 
           找到分裂点之后，如果分裂点是给定分支的最新commit，那么直接打印
           "Given branch is an ancestor of the current branch."
           如果分裂点是当前分支的最新commit，那么直接checkout到给定分支，并打印"Current branch fast-forwarded."
           然后设计一个
           merge_commit(Commit splitPoint,Commit current_branch_latest,Commit given_branch_latest)
           首先应该把所有在这三个commit中出现过的文件名称放到一个set里面去，并new一个Map
           然后我们遍历这个set,首先是文件在三个commit中都存在的情况
           此时如果1==2，且2！=3，则采用3
           如果1==3,且3!=2,则采用2
           如果2==3，则采用任意一个加到新建的Map里面
           然后就来到了文件不并存在三个commit中的情况
           如果在只在当前commit有这个文件，则加入Map
           如果只在给定分支有这个文件，则checkout该文件，加入暂存区，并加入Map
           如果1==2,且不存在于3.则在工作区删除文件，并添加到file_to_rm,
           如果1==3，且不存在于2，无操作
           而关于最复杂的处理冲突的步骤，直接用get(key)函数，因为不存在的话会直接返回null
           还有一个Object.equals(a,b),可以比较空和非空的情况
           此时应该是三个get_file_hash两两不等，
           我们就来拼接一个文件（还要设计一个get_file_content函数，如果file_hash为null,则内容页返回空)
           然后把这个修改后的文件内容写入工作区，暂存，并加入Map
           在这里我们设计一个boolean变量，一旦发生合并冲突，boolean conflictOccurred = true;
           还有创建commit的部分，未完待续
           关于创建一个新的commit,这个commit的message是
           Merged [given branch name] into [current branch name].
           关于有两个parent的情况，呜呜呜呜呜呜，需要把commit类重构 */
        StagingArea stagingArea = StagingArea.loadFromFile();
        Commit currentCommit = getHeadCommit();
        File branchFile = new File(BRANCH_DIR, branchName);
        if (!branchFile.isFile()) {
            exitWithError("A branch with that name does not exist.");
        }
        Commit givenBranchHeadCommit = getWantedHeadCommit(branchName);
        if (!stagingArea.getFileToAdd().isEmpty() 
                || !stagingArea.getFileToRemove().isEmpty()) {
            exitWithError("You have uncommitted changes.");
        }
        String currentBranchName = getCurrentBranchName();
        if (currentBranchName.equals(branchName)) {
            exitWithError("Cannot merge a branch with itself");
        }

        haveUntrackedFiles(currentCommit, givenBranchHeadCommit);
        Commit splitPoint = findSplitPoint(currentCommit, branchName);
        if (isHeadCommit(splitPoint) != null) { //返回的是branchname
            if (branchName.equals(isHeadCommit(splitPoint))) {
                exitWithError("Given branch is an ancestor of the current branch.");
            }
            if (currentBranchName.equals(isHeadCommit(splitPoint))) {
                checkOutBranch(branchName);
                exitWithError("Current branch fast-forwarded.");
            }
        }
        mergeCommit(splitPoint, currentCommit, givenBranchHeadCommit, branchName);
    }

    public static void addRemote(String remoteName, String remotePath) { 
        //注意remotepath给的不一定是绝对路径
        List<String> allRemoteFiles = plainFilenamesIn(REMOTE_PATH_DIR);
        if (allRemoteFiles.contains(remoteName)) {
            exitWithError("A remote with that name already exists.");
        }
        File remoteFile = join(REMOTE_PATH_DIR, remoteName);
        writeContents(remoteFile, remotePath);
    }

    public static void removeRemote(String name) {
        List<String> allRemoteFiles = plainFilenamesIn(REMOTE_PATH_DIR);
        if (!allRemoteFiles.contains(name)) {
            exitWithError("A remote with that name does not exist.");
        }
        File remoteFile = join(REMOTE_PATH_DIR, name);
        delete(remoteFile);
    }

    private static boolean delete(File file) {
        if (!file.isDirectory()) {
            return file.delete();
        } else {
            return false;
        }
    }

    public static void pushRemote(String remoteName, String remoteBranchName) {
        File remoteFile = getAndValidateRemotePath(remoteName);
        Commit currentCommit = getHeadCommit();
        if (!remoteFile.exists()) {
            exitWithError("Remote directory not found.");
        } else {
            File remoteBranchFile = join(remoteFile, "refs", "heads");
            List allBranchName = plainFilenamesIn(remoteBranchFile);
            if (!allBranchName.contains(remoteBranchName)) {
                Queue<Commit> parentQueueToAdd = getFullParentQueue(currentCommit, GITLET_DIR);
                saveAllCommitAndBlobPush(parentQueueToAdd, remoteFile);
            } else {
                /*分别得到两个头结点*/
                Commit remoteHeadCommit = getRemoteWantedHeadCommit(remoteBranchName, remoteFile);
                Set<String> currentCommitHashSet = getParentHashSet(currentCommit);
                //得到当前头结点的所有父节点的hash值
                String remoteHeadCommitHash = getHashOf(remoteHeadCommit);
                if (!currentCommitHashSet.contains(remoteHeadCommitHash)) {
                    exitWithError("Please pull down remote changes before pushing.");
                }
                Queue<Commit> parentQueueToAdd = getParentQueueBetween
                        (currentCommit, remoteHeadCommit);
                //得到两个commit之间隔着的所有commit
                saveAllCommitAndBlobPush(parentQueueToAdd, remoteFile);
            }
            //还要记得更新指针
            String headHash = getHeadCommitHash();
            File remoteBranch = join(remoteFile, "refs", "heads", remoteBranchName);
            writeContents(remoteBranch, headHash);
            /*
           首先得到远程分支头指针
           得到当前所在分支的所有父commit的hash列表（也许应该用queue结构来储存，因为只需要复制头节点之后的commit，
           如果远程头指针在这个列表里，才继续
           把头结点之后的commit全部复制到远程仓库的.gitlet/objects
           然后再去读取这个头commit对象，复制其跟踪的blob到远程仓库的.gitlet/objects
           但是注意不要重复
           然后更新远程分支指针
           
         */
        }
    }

    public static void fetchRemote(String remoteName, String remoteBranchName) {
        /*
             感觉和push很像，只是拿文件和放文件的地方反过来了，有些细节需要处理
             首先，从REMOTE_PATH_DIR的remotename里面获取路径
             如果对应仓库不存在，则Remote directory not found.
             然后去远程仓库找对应的分支文件（里面装的是头commit的hash），如果
             文件不存在，That remote does not have that branch.
             否则获取头commit
             获取头commit后找到其所有的父commit
             然后获取所有父commit的hash值
             然后到本地仓库的COMMIT_DIR,得到本地hash值
             来一个循环，把所有不在本地的都加到一个队列里面去
             然后就可以调用函数
             最后更新指针
         */
        File remoteFile = getAndValidateRemotePath(remoteName);
        File remoteBranchFile = join(remoteFile, "refs", "heads");
        List allBranchName = plainFilenamesIn(remoteBranchFile);
        if (!allBranchName.contains(remoteBranchName)) {
            exitWithError("That remote does not have that branch.");
        }
        Commit remoteHeadCommit = getRemoteWantedHeadCommit(remoteBranchName, remoteFile); 
        //远程头节点
        Queue<Commit> parentQueueToLoad = getFullParentQueue(remoteHeadCommit, remoteFile);
        Map<Commit, String> parentQueueToLoadMap = new HashMap<>();
        Queue<Commit> parentQueueToLoadFinal = new LinkedList<>();
        while (!parentQueueToLoad.isEmpty()) {
            Commit commitToHandle = parentQueueToLoad.poll();
            String commitToHandleHash = getHashOf(commitToHandle);
            parentQueueToLoadMap.put(commitToHandle, commitToHandleHash);
        } //现在得到了远程对应branch所有父节点的hash与commit的map
        List<String> localCommitHash = plainFilenamesIn(COMMIT_DIR);
        for (Map.Entry<Commit, String> singleCommitToAdd : parentQueueToLoadMap.entrySet()) {
            if (!localCommitHash.contains(singleCommitToAdd.getValue())) { //这个commit在本地没有
                parentQueueToLoadFinal.add(singleCommitToAdd.getKey());
            }
        }
        saveAllCommitAndBlobFetch(parentQueueToLoadFinal, remoteFile);
        //接下来更新指针
        String remoteHeadHash = getHashOf(remoteHeadCommit);
        File remoteBranch = join(BRANCH_DIR, remoteName, remoteBranchName);
        File remote = join(BRANCH_DIR, remoteName);
        remote.mkdir();
        writeContents(remoteBranch, remoteHeadHash);
    }

    public static void pull(String remoteName, String remoteBranchName) {
        fetchRemote(remoteName, remoteBranchName);
        String fetchedBranchName = remoteName + "/" + remoteBranchName;
        merge(fetchedBranchName);

    }
}


