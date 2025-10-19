# Gitlet设计文档

**作者**:张婧
**日期**:2025 09 23
## 背景与问题描述
在现代代码开发中，代码版本控制是十分重要的。本项目旨在通过从零开始构建一个简化版的Git，即Gitlet来
达到深入理解分布式版本控制系统核心概念与实现机制的目的。
## 顶层设计与思想
### 序列化
在代码运行的过程中，运行结果通常只存在于内存之中，运行结束之后，运行结果便消失了，无法保存进度。而git系统则要求
我们保留之前的运行结果以便日后继续运行。在java中，实现持久化的本质就是将数据储存在文件系统中。在java中，解决这一问题的方案
是实现序列化。通过序列化，我们可以将对象转化成一系列字节，储存在文件中，当需要用原始对象时，我们可以反序列化，读取原始对象。
### 快照
Gitlet的核心思想与Git是一样的，即基于快照对进行版本控制。通俗来说，就是在项目开发的过程中，可以保存每一个想要记录下来的项目状态，
而Commit命令就像是给项目“拍了一张照”，记录下某个时间节点项目的所有细节，并在以后想要重新查看的时候回溯。并且处于安全考虑，一旦进行了
某一个Commit，那么项目在这一时刻的状态就被永久的保存下来了，是不可更改和删除的，这也保证了历史记录的可追溯。
### 哈希
在本项目中，需要大量的文件寻址工作，而文件寻址就是根据文件的路径。我们需要把对象序列化写入文件，并且在需要的时候读取他。要想通过一个文件名
精准的找到对应的对象，简单地命名方式是难以实现的。我们需要一种从对象到文件名的唯一的映射方式。本项目与git一样，都是通过SHA-1实现（即安全哈希函数）。
该函数可以从任何字节序列生成160位整数哈希值。该函数的特性在于两个具有完全相同SHA-1 哈希值的概率极小，只存在理论上的可能。

## 文件结构设计
由上所述，存档的本质就是把数据储存在文件系统中，因此该项目也是在原项目目录下创建一个文件夹，里面放置了所有想要保存的记录。后续所有操作都是基于文件的，
因此有一个结构清晰的文件结构设计十分重要。
**文件结构**
```
.gitlet/
├── HEAD     记录当前所在分支是哪个
├── index    暂存区内容
├── objects/ 放置blobs和commits,以hash值为文件命名
│   ├── commit-储存commit
│   └── blobs-放置储存文件
└── refs/     记录每个branch的头指向哪里（例如master这个文件里面储存的是master分支最新一次提交的hash值）
│   └── heads/
│        ├── master
│        └── dev
└── remotes/  记录远程仓库名与远程仓库地址之间的对应关系
```

概括来讲，该项目的运作思路大致如下：
* objects文件夹里面有两个文件夹，分别是blobs和commit。

其中blobs文件夹用来放置序列化后的文本文件，以根据其序列化后的字节计算出来的hash值命名

commit文件夹则用来放置序列化后的commit对象，同样用其序列化后的字节计算出来的hash值命名
* index代表暂存区，里面记录了我们需要add和remove的文件，用map来实现文件名到具体内容的映射
* refs/heads文件夹则更像是存放了以分支名命名的指针。之所以可以把他理解为指针，是因为refs/heads
文件夹中的文件都是以分支名命名的，其内容则是该分支最新一次commit的hash值
* HEAD这个文件则记录了我们当前所在的位置，ref: refs/heads/master即代表我们当前工作区处在master这个分支上。
由于本项目不存在




## 类和数据结构
我们需要以下几个类来完成project
### Main.java
负责读取命令行的输入，并调取相应的函数，起到一个派发任务的作用。也涉及到处理一些在命令行输入的时候的错误情况。
### Commit.java
commit是我们在设计gitlet时最主要需要关注的对象，我们设计一个同名类来模拟其行为。
在我们的设计中，一个commit对象需要包含以下几个属性：
private Date timestamp

private List<String> parent //使用List是考虑到后续的merge操作可能会导致一个Commit有两个父Commit

private Map<String,String> trackedFile

private String message

要注意的是，在我们的上层设计思想中，是需要用到指针的，因为我们希望能根据一个commit找到其父节点，(commit的结构看起来有点像LinkedList),
并且希望能根据在对象中记录的跟踪文件信息，索引到对应的文件。

但直接使用指针在本项目中是行不通的。因为Commit对象是需要序列化的，而序列化一个对象的过程中，不仅仅会序列化这个单个对象，还会序列化其内部指针所指向的
所有对象，并且这种影响是链式传导的。这意味着如果我们真的使用指针结构来设计，每当我们序列化一个新的commit，我们便会把该commit，其所有父节点以及所有这些
commit所指向的文件全部序列化一遍。这会导致我们需要很多空间来储存重复的内容。当commit的数量比较多是，这样的结构明显是效率很低的。

在本项目中，我们不使用指针来直接引用，而是通过记录某个对象序列化之后，用SHA-1函数计算出的唯一对应的hash值，来代表对对象的引用。即内容寻址储存。
此时，hash值扮演了多重角色。

1.唯一标识符，因为两个不同对象拥有一样的hash值的可能性几乎为0，只存在理论上的可能。

2.对对象的引用，可以用来查找和获取对象

3.唯一性校验，由于当某个文件内容发生变化时（即使只是一个字节的变化），计算出来的hash值也与之前完全不同。所以我们可以通过在获取数据后重新计算hash值的
方式，对数据进行完整性校验。

另外，由于用户在与该系统交互的过程中，一般希望用对文件的命名来指代某一特定文件，而非该文件对应的hash值。所以我们需要一个结构，记录下文件名与其对应版本（hash值）
的对应关系。在该项目的设计中，我们使用Map，用文件名作为key值，用其hash值作为value。并且，考虑到Hashmap中元素顺序不确定，这可能导致我们序列化HashMap再反序列化之后，
得到不相同的校验和。因此我们选择使用TreeMap,因为顺序是确定的。

**Commit中的method设计**

Commit中包含三个构造函数，分别是针对init时的commit提交，一般commit提交以及merge过程中创建的commit这三种不同情况。
需要注意的是，为了保证commit的安全性，我们在设计返回trackedfile的函数的过程中，没有直接返回对trackedfile的引用，而是返回了
一个副本。这样外界就不能随意修改某次commit中跟踪的文件信息。

### StagingArea.java

由于在gitlet的使用过程中，用户也经常需要与暂存区进行一些交互，所以我设计一个StagingArea类来模仿暂存区的行为。

一个StagingArea对象包含两个属性：

fileToAdd，记录添加到暂存区以备下次commit上传的文件名以及对应的版本，用TreeMap来实现

fileToRemove,记录下次commit需要移除的文件名，用TreeSet来实现(因为想要移除某文件时，无需记录版本)

有以下几个methods

首先是构造函数，用于初始化暂存区

然后是loadFromFile函数，用于把暂存区从文件中加载出来，saveToFile函数，用于把暂存区写到文件中去

getFileToAdd,用于获取暂存区储存的fileToAdd，getFileToRemove,用于获取暂存区储存的fileToRemove

并且有一点与Commit类不同的是，由于我们需要经常向暂存区添加和删除文件，并且暂存区也是时时变化的。所以我们的getFileToAdd以及getFileToRemove函数
都是直接返回的对于fileToAdd和fileToRemove的引用，可以直接对其进行修改。

clear用于清空暂存区
### Respository.java

项目的核心逻辑，相当于核心指挥，会包含诸如init,add,commit等所有可以输入的命令的操作方法，

主要对应的是main类里面会调用的一些函数

### Utils.java

序列化和反序列化，以及生成hash值的工具箱

### CommitManager.java

包含一些为了简化Repository类的逻辑而封装的函数

public static Commit getHeadCommit() 返回HEAD指向的commit

public static Commit getWantedHeadCommit(String branchName)返回指定分支名头部commit

public static Commit getRemoteWantedHeadCommit(String branchName, File repoFile)
返回远程仓库指定分支的头部commit

public static Commit getFirstParentCommit(Commit currentCommit)用来回溯第一父节点
（在log命令使用）

public static String getHeadCommitHash()返回HEAD指向的commit的Hash值

public static void initTool() 将repository中init的具体逻辑进行封装

## 实现的功能概览

**init**

与实际git类似，在当前目录中创建一个新的 Gitlet 版本控制系统。该系统将自动以一次提交（initial commit)启动

**add [file name] **

创建一个文件副本，添加到暂存区

**commit [message]**

创建一次新的commit

**rm [filename]**

与真实git类似，按照规则根据文件状态从暂存区或工作目录移除

**log**

从当前头提交开始，沿着提交树向后显示每个提交的信息，直至初始提交(忽略合并提交中找到的任何第二个父提交)

**global log**

类似 log，但会显示所有提交的信息（不一定按顺序）

**find [commit message]**

打印所有包含指定message的提交ID（即sha1值），每行一个。如果有多个这样的提交，则将ID打印在不同的行上。

**status**

显示所有branches（用*标记当前分支）以及暂存区的情况（Staged Files， Removed Files ，Modifications Not Staged For Commit，Untracked Files）

**checkout -- [file name]**

将文件在 head 提交中的版本放入工作目录中，并覆盖工作目录中已存在的版本（如果存在）。

**checkout [commit id] -- [file name]**

获取指定 ID 提交的文件版本，并将其放入工作目录，如果工作目录下已有文件版本，则覆盖.

**checkout [branch name]**

将指定分支头部提交的所有文件放入工作目录，并覆盖已存在的文件版本。并把签出分支设为当前分支。

**branch [branch name]**

创建一个指定名称的新分支，并将其指向当前的主提交。

**reset [commit id]**

检出指定提交所跟踪的所有文件。移除该提交中不存在的跟踪文件。同时将当前分支的头节点移动到该提交节点。

**merge [branch name]**

该命令的规则比较复杂，有很多种边界情况需要处理，此处不一一详述。其规则的框架与真实的git是相似的，只是在处理冲突时，本项目只是简单地把两个内容不同的文件
写入同一个文件，并交由用户处理。而真实的git则会逐行比较这两份文件之间有什么异同，从而决定如何进行合并。而该项目主要目的是理解git的总体设计哲学，
因而将这一部分简化处理。

**add-remote [remote name] [name of remote directory]/.gitlet**

将指定的登录信息保存在指定的远程名称下。尝试从指定的远程名称推送或拉取数据时，将尝试使用此 .gitlet 目录。

**rm-remote [remote name]**

删除与给定远程名称关联的信息。

**push [remote name] [remote branch name]**

与Git类似，尝试将当前分支的提交附加到指定远程分支的末尾

**fetch [remote name] [remote branch name]**

与git类似，将远程 Gitlet 仓库中的提交迁移到本地 Gitlet 仓库。

**pull [remote name] [remote branch name]**

按照 fetch 命令获取分支 [remote name]/[remote branch name] ，然后将该获取合并到当前分支。

### 局限与未来展望

局限与不足 

存储冗余: 对文件的每次修改都保存完整副本，空间效率低。

合并能力弱: 无法自动处理复杂的分支合并冲突。

伪网络: remote命令仅为本地模拟，无法实现真正的网络协作。

未来展望 

存储优化: 引入差异存储（Delta Compression），减少仓库体积。

智能合并: 实现三方合并算法，并能标记文件冲突供用户解决。

真实网络: 构建客户端-服务器架构，实现真正的 push 和 pull。
