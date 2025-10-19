package gitlet;

import java.text.SimpleDateFormat;
import java.util.*;
import java.io.Serializable;

/**
 * Represents a gitlet commit object.
 * does at a high level.
 *
 * @author 张婧
 */
public class Commit implements Serializable {
    /**
     * 时间
     */
    private Date timestamp;
    /**
     * 每个commit都有其parent
     */
    private List<String> parent;
    /**
     * 还需要一个键值对的集合来保存所有体检文件的名称以及其对应的版本
     */
    private Map<String, String> trackedFile;
    /**
     * 还需要一个描述某次commit的message
     */
    private String message;
    private static final long serialVersionUID = 1L;

    public Commit() {
        this.message = "initial commit";
        this.parent = new ArrayList<>();
        this.trackedFile = new TreeMap<>();
        this.timestamp = new Date(0L);
    }

    public Commit(String parent, String message, Map<String, String> trackedFile) {
        this.parent = new ArrayList<>();
        this.parent.add(parent);
        this.message = message;
        this.trackedFile = trackedFile;
        this.timestamp = new Date();
    }

    public Commit(String parent1, String parent2, String message, Map<String, String> trackedFile) {
        this.parent = new ArrayList<>();
        this.parent.add(parent1);
        this.parent.add(parent2);
        this.message = message;
        this.trackedFile = trackedFile;
        this.timestamp = new Date();
    }

    public Map<String, String> getTrackedFileCopy() {
        if (trackedFile == null) {
            return new TreeMap<>();
        }
        return new TreeMap<>(this.trackedFile);
        //记得要返回一个副本，因为一个commit一旦提交，是不能修改的
    }

    public String getFirstParent() {
        if (parent == null || parent.isEmpty()) {
            return null;
        }
        return parent.get(0);
    }

    public List<String> getParentList() {
        if (parent == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(this.parent);
    }

    public String getTimestamp() {
        // 使用项目要求的格式来格式化日期
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return formatter.format(this.timestamp);
    }

    public String getMessage() {
        return message;
    }


}
