package gitlet;

import static gitlet.Utils.*;
import static gitlet.Repository.*;
import static gitlet.StagingArea.*;
import static gitlet.MainLogicTool.*;
import static gitlet.GitletCore.*;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author 张婧
 */

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
            return;
        }
        // 创建 Main 类的实例来处理命令
        Main gitletApp = new Main();
        gitletApp.executeCommand(args);
    }
    public void executeCommand(String[] args) {
        String command = args[0];

        // 除了 init，所有命令都需要在一个初始化的 Gitlet 目录中运行
        if (!command.equals("init") && !Repository.GITLET_DIR.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
            return;
        }

        switch (command) {
            case "init":
                handleInit(args);
                break;
            case "add":
                handleAdd(args);
                break;
            case "commit":
                handleCommit(args);
                break;
            case "rm":
                handleRm(args);
                break;
            case "log":
                handleLog(args);
                break;
            case "global-log":
                handleGlobalLog(args);
                break;
            case "find":
                handleFind(args);
                break;
            case "status":
                handleStatus(args);
                break;
            case "checkout":
                handleCheckout(args);
                break;
            case "branch":
                handleBranch(args);
                break;
            case "rm-branch":
                handleRmBranch(args);
                break;
            case "reset":
                handleReset(args);
                break;
            case "merge":
                handleMerge(args);
                break;
            case "add-remote":
                handleAddRemote(args);
                break;
            case "rm-remote":
                handleRmRemote(args);
                break;
            case "push":
                handlePush(args);
                break;
            case "fetch":
                handleFetch(args);
                break;
            case "pull":
                handlePull(args);
                break;
            default:
                exitWithError("No command with that name exists.");
        }
    }

    // --- 命令处理辅助方法 (Private Helper Methods) ---

    private void handleInit(String[] args) {
        validateNumArgs(args, 1);
        init();
    }

    private void handleAdd(String[] args) {
        validateNumArgs(args, 2);
        String fileToAdd = args[1];
        add(fileToAdd);
    }

    private void handleCommit(String[] args) {
        validateNumArgs(args, 2);
        String message = args[1];
        if (message.isEmpty()) {
            exitWithError("Please enter a commit message.");
        }
        StagingArea stagingArea = loadFromFile();
        if (stagingArea.getFileToAdd().isEmpty() && stagingArea.getFileToRemove().isEmpty()) {
            exitWithError("No changes added to the commit.");
        }
        commit(message);
    }

    private void handleRm(String[] args) {
        validateNumArgs(args, 2);
        String fileToRemove = args[1];
        rm(fileToRemove);
    }

    private void handleLog(String[] args) {
        validateNumArgs(args, 1);
        log();
    }

    private void handleGlobalLog(String[] args) {
        validateNumArgs(args, 1);
        globalLog();
    }

    private void handleFind(String[] args) {
        validateNumArgs(args, 2);
        String commitMessage = args[1];
        find(commitMessage);
    }

    private void handleStatus(String[] args) {
        validateNumArgs(args, 1);
        status();
    }

    private void handleCheckout(String[] args) {
        if (args.length == 3 && args[1].equals("--")) {
            String fileName = args[2];
            checkoutCurrentCommitFile(fileName);
        } else if (args.length == 4 && args[2].equals("--")) {
            String commitId = getFullCommitHash(args[1]);
            if (commitId == null) {
                exitWithError("No commit with that id exists.");
            }
            String fileName = args[3];
            checkoutGivenCommitFile(commitId, fileName);
        } else if (args.length == 2) {
            String branchName = args[1];
            checkOutBranch(branchName);
        } else {
            exitWithError("Incorrect operands.");
        }
    }

    private void handleBranch(String[] args) {
        validateNumArgs(args, 2);
        String branchName = args[1];
        makeBranch(branchName);
    }

    private void handleRmBranch(String[] args) {
        validateNumArgs(args, 2);
        String branchNameToRm = args[1];
        removeBranch(branchNameToRm);
    }

    private void handleReset(String[] args) {
        validateNumArgs(args, 2);
        String commitId = getFullCommitHash(args[1]);
        if (commitId == null) {
            exitWithError("No commit with that id exists.");
        }
        resetACommit(commitId);
    }

    private void handleMerge(String[] args) {
        validateNumArgs(args, 2);
        String branchNameToMerge = args[1];
        merge(branchNameToMerge);
    }

    private void handleAddRemote(String[] args) {
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String remotePath = args[2];
        addRemote(remoteName, remotePath);
    }

    private void handleRmRemote(String[] args) {
        validateNumArgs(args, 2);
        String remoteName = args[1];
        removeRemote(remoteName);
    }

    private void handlePush(String[] args) {
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String branchName = args[2];
        pushRemote(remoteName, branchName);
    }

    private void handleFetch(String[] args) {
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String branchName = args[2];
        fetchRemote(remoteName, branchName);
    }

    private void handlePull(String[] args) {
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String branchName = args[2];
        pull(remoteName, branchName);
    }
    public static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            exitWithError("Incorrect operands.");
        }
    }
}    
