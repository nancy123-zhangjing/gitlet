package gitlet;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map;
import java.util.Set;
import java.io.Serializable;

import static gitlet.Repository.*;
import static gitlet.Utils.*;

/**
 * Represents a gitlet staging_area.
 * does at a high level.
 *
 * @author 张婧
 */

public class StagingArea implements Serializable {
    /**
     * 感觉Staging_area这个类和commit有点类似，甚至更简单
     * 暂存区是我们的工作区和commit之间的桥梁
     * 哪些命令会改变暂存区呢
     * 首先是add,我们需要记录被add文件的名字和具体内容，与commit类似，
     * 同样需要一个Map<String,String>，记录文件的名称和对应的Hash值，
     * 然后还有rm，我们需要记录一个待删除文件的清单，需要的结构是Set<String>
     * 有一个点，其实你可以把staging_area这个类看成一个可实例化成对象并且写进index中的东西
     */
    private static final long serialVersionUID = 1L;
    private Map<String, String> fileToAdd;
    private Set<String> fileToRemove;

    public StagingArea() {
        fileToAdd = new TreeMap<>();
        fileToRemove = new TreeSet<>();
    }
    /**
     * 把对象从文件中加载出来
     */
    public static StagingArea loadFromFile() {
        return readObject(STAGING_AREA_FILE, StagingArea.class);
    }

    /**
     * 把对象写到文件里面去
     */
    public void saveToFile() {
        writeObject(STAGING_AREA_FILE, this);
    }

    public Map<String, String> getFileToAdd() {
        if (fileToAdd == null) {
            return new TreeMap<>();
        }
        return this.fileToAdd;
    }

    public Set<String> getFileToRemove() {
        if (fileToAdd == null) {
            return new TreeSet<>();
        }
        return this.fileToRemove;
    }

    public void clear() {
        this.fileToAdd.clear();
        this.fileToRemove.clear();
    }

}
