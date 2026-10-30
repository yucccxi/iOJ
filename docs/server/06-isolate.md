# Isolate 判题沙箱使用指南

本文介绍如何在 Linux 上安装 Isolate，在沙箱内编译和运行 C++ 程序，读取执行结果，并接入 Java 判题 worker。

安装示例适用于 Debian 13（trixie）、amd64 架构。命令按 Isolate 官方当前文档整理于 2026 年 10 月 30 日，并已在本项目的判题机上实际执行验证（Isolate 2.7）；其他发行版或旧版 Isolate 的安装与 cgroup 配置可能不同。

## 1 Isolate 的作用与边界

Isolate 是用于执行不可信程序的 Linux 沙箱，利用 namespaces、cgroups 等机制隔离环境、限制资源。它负责运行隔离和资源统计，不负责题目管理、任务分发、答案比较或最终判定 AC。

在 OJ 中，职责可以这样划分：

| 组件 | 职责 |
| --- | --- |
| OJ 后端 | 保存提交、分配任务、保存判题结果 |
| 判题 worker | 准备文件、调用 Isolate、解析运行信息、比较答案 |
| Isolate | 隔离运行环境、限制资源、输出执行元数据 |

编译用户代码也要放进沙箱。不要直接在宿主机编译用户代码后，仅隔离最终可执行文件。

沙箱仍然共享宿主机内核，不能视为绝对安全保证。公开运行用户代码时使用独立判题机器，持续更新系统与 Isolate；不要向沙箱暴露数据库、节点凭据或个人目录。

## 2 安装与环境检查

### 2.1 确认系统

```bash
cat /etc/os-release
dpkg --print-architecture
uname -r
stat -fc %T /sys/fs/cgroup
```

下面的软件源针对 Debian 13 和 amd64。最后一条命令在 cgroup v2 环境通常输出 `cgroup2fs`。当前官方 Isolate 的 cgroup 模式要求 cgroup v2，内存统计还要求 Linux 5.19 或更新版本。

### 2.2 添加官方软件源

```bash
sudo apt update
sudo apt install curl ca-certificates g++

sudo install -d -m 0755 /etc/apt/keyrings

curl -fsSL https://www.ucw.cz/isolate/debian/signing-key.asc \
    | sudo tee /etc/apt/keyrings/isolate.asc >/dev/null
```

写入软件源配置：

```bash
sudo tee /etc/apt/sources.list.d/isolate.sources >/dev/null <<'EOF'
Types: deb
URIs: http://www.ucw.cz/isolate/debian/
Suites: trixie-isolate
Components: main
Architectures: amd64
Signed-By: /etc/apt/keyrings/isolate.asc
EOF

sudo apt update
sudo apt install isolate
```

该配置来自官方 README。安装其他系统时，应先核对官方支持的软件包，而不是直接复用 `trixie-isolate`。

### 2.3 启动 cgroup 管理服务

```bash
sudo systemctl start isolate.service
systemctl status isolate.service --no-pager
isolate --check-config
```

使用 systemd 时，当前官方版本通过 `isolate.service` 和 keeper 进程维护委派给 Isolate 的 cgroup。不要为排查权限错误而给整个 Java worker 开放 root 权限。

Isolate 按安装配置使用特权建立隔离环境，再将被执行程序切换到受限 UID。官方包应处理所需安装权限；遇到权限异常时先检查包、配置和服务日志。

## 3 跑通第一个 C++ 程序

以下命令在同一个 Bash 会话中依次执行。使用沙箱编号 `0`，请确保没有其他任务正在使用它。示例复用一个沙箱便于入门，正式判题应分离编译环境，并为各测试点准备干净运行环境。

### 3.1 初始化

```bash
BOX_ROOT=$(isolate --cg --box-id=0 --init)
printf '%s\n' "$BOX_ROOT"
```

必须确认初始化成功、输出是有效目录后，再执行后续文件操作。自动化程序应检查退出码及路径，避免初始化失败后继续处理。

| 位置 | 工作目录路径 |
| --- | --- |
| 宿主机 | `"$BOX_ROOT/box"` |
| 沙箱内 | `/box` |

不要硬编码初始化输出的绝对路径。后续使用返回值定位工作目录。

### 3.2 写入源代码和输入

```bash
cat > "$BOX_ROOT/box/main.cpp" <<'EOF'
#include <iostream>
using namespace std;

int main()
{
    long long a,b;
    cin>>a>>b;
    cout<<a+b<<'\n';
    return 0;
}
EOF

printf '2 3\n' > "$BOX_ROOT/box/input.txt"
```

标准答案放在沙箱外，不要把完整题目数据目录和标准答案一起映射给用户程序。

### 3.3 在沙箱内编译

```bash
isolate --cg --box-id=0 \
    --processes=16 \
    --time=10 \
    --wall-time=30 \
    --cg-mem=524288 \
    --fsize=16384 \
    --env=PATH=/usr/bin:/bin \
    --env=TMPDIR=/box \
    --stdin=/dev/null \
    --stdout=/box/compile.out \
    --stderr=/box/compile.err \
    --meta="$PWD/compile.meta" \
    --run -- \
    /usr/bin/g++ /box/main.cpp -std=c++17 -O2 -o /box/main
```

编译器会创建子进程，因此允许最多 16 个进程或线程。编译的时间、内存限制与运行限制分开设置，示例值不是所有题目或语言的通用配置。

查看结果：

```bash
cat compile.meta
cat "$BOX_ROOT/box/compile.err"
```

确认编译成功后再继续。自动化时应立即保存命令退出码，不要执行 `cat` 后再读取 `$?`，那样拿到的是 `cat` 的退出码。

编译器拒绝源代码属于 CE；沙箱内部错误、编译器缺失等属于系统故障，不应统一标记为 CE。

### 3.4 在沙箱内运行

```bash
isolate --cg --box-id=0 \
    --processes=1 \
    --time=1 \
    --wall-time=5 \
    --cg-mem=262144 \
    --fsize=1024 \
    --stdin=/box/input.txt \
    --stdout=/box/output.txt \
    --stderr=/box/runtime.err \
    --meta="$PWD/run.meta" \
    --run -- \
    /box/main
```

查看输出：

```bash
cat "$BOX_ROOT/box/output.txt"
cat "$BOX_ROOT/box/runtime.err"
cat run.meta
```

`output.txt` 应包含 `5`。程序正常结束只表示执行成功，还要由 worker 比较输出，才能判定 AC 或 WA。

### 3.5 清理

```bash
isolate --cg --box-id=0 --cleanup
```

无论成功、超时、异常还是取消，都应执行清理。Java 中放在 `finally`；只有清理完成后，才释放该 box-id。清理失败的执行槽应暂停复用并报警。

## 4 常用参数

| 参数 | 含义 |
| --- | --- |
| `--box-id=0` | 沙箱编号，同机并发任务使用不同编号 |
| `--cg` | 启用 cgroup，初始化、运行和清理时保持一致 |
| `--time=1` | CPU 时间限制 1 秒 |
| `--wall-time=5` | 实际经过时间限制 5 秒，包含等待和休眠 |
| `--cg-mem=262144` | 整个 cgroup 内存限制，单位 KB，示例约 256 MiB |
| `--mem=262144` | 地址空间限制，不能当作实际物理内存统计 |
| `--processes=16` | 最多允许 16 个进程或线程 |
| `--fsize=1024` | 每个文件的大小限制，单位 KB |
| `--quota=blocks,inodes` | 初始化时设置磁盘配额，需要底层文件系统支持 |
| `--stdin=/box/input.txt` | 标准输入文件，路径位于沙箱内 |
| `--stdout=/box/output.txt` | 标准输出文件，路径位于沙箱内 |
| `--stderr=/box/runtime.err` | 标准错误文件，路径位于沙箱内 |
| `--meta=/path/run.meta` | 宿主机上的执行元数据文件 |
| `--env=NAME=value` | 显式设置一个环境变量 |
| `--dir=/data=/host/data` | 映射目录，默认只读；仅暴露必要目录 |

CPU 时间和实际经过时间应同时限制，避免休眠程序长期占用任务槽。不要使用不带数量的 `--processes`，它会允许任意数量的进程。

`--fsize` 只限制单个文件，无法阻止程序创建大量小文件。正式环境还要限制总磁盘容量、inode 数量，并核对 `/tmp` 和共享内存等可写区域的约束。

默认创建隔离网络命名空间，无法访问外部网络。不要给判题程序添加 `--share-net`。不要通过 `--full-env` 将 worker 的凭据传入沙箱。

## 5 元数据解析与判题结果

meta 文件是 `key:value` 格式，程序中按第一个冒号拆分每行，不要依赖字段顺序，不要假定所有字段都存在。

| 字段 | 含义 |
| --- | --- |
| `time` | CPU 用时，秒 |
| `time-wall` | 实际经过时间，秒 |
| `cg-mem` | cgroup 内存用量，KB |
| `max-rss` | 进程最大常驻内存，KB |
| `exitcode` | 程序正常退出时的退出码 |
| `exitsig` | 导致程序终止的信号 |
| `cg-oom-killed` | 存在时表示发生了 cgroup OOM kill |
| `status` | 异常状态码 |
| `message` | 面向人的说明，不能用于稳定的程序判断 |

| `status` 值 | 含义 | worker 处理方向 |
| --- | --- | --- |
| `TO` | 超时 | 通常判为 TLE |
| `RE` | 非零退出 | 通常判为 RE |
| `SG` | 被信号终止 | 根据信号、OOM 等信息进一步分类 |
| `XX` | 沙箱内部错误 | 系统故障，记录原因并按策略重试 |
| 不存在 | 正常运行时可能没有此字段 | 继续检查退出码和答案 |

Isolate 自身退出码为 0 表示程序正确结束，1 表示程序执行失败，其他返回码表示内部错误。最终分类要结合 meta，不能只看一个退出码。

不要将所有 SIGSEGV 都判为 MLE；非法内存访问同样可能触发该信号。内存分配失败也不一定触发 OOM kill，MLE 需要统一且经过验证的分类策略。

如果反复使用同一个 cgroup 运行程序，缓存可能影响后续 `cg-mem` 统计。正式评测应使用干净环境，并验证内存统计口径。

## 6 接入 Java 判题 worker

Java 负责构造固定参数、启动 Isolate、等待完成、解析结果和清理。不要把用户输入拼接进 `sh -c`。

下面片段只演示运行阶段的进程调用；假定沙箱、输入和可执行文件已准备好，`logPath`、`metaPath` 位于沙箱外，`boxId` 由 worker 分配。

```java
ProcessBuilder builder = new ProcessBuilder(
        "isolate",
        "--cg",
        "--box-id=" + boxId,
        "--processes=1",
        "--time=1",
        "--wall-time=5",
        "--cg-mem=262144",
        "--fsize=1024",
        "--stdin=/box/input.txt",
        "--stdout=/box/output.txt",
        "--stderr=/box/runtime.err",
        "--meta=" + metaPath.toAbsolutePath(),
        "--run", "--", "/box/main"
);

builder.redirectErrorStream(true);
builder.redirectOutput(logPath.toFile());

Process process = builder.start();
int isolateExitCode = process.waitFor();
```

需要使用 `java.lang.ProcessBuilder`、`java.lang.Process`，路径通常由 `java.nio.file.Path` 表示。此片段省略外层异常处理和 worker 级看门狗，不是完整生产实现。

将管理进程输出重定向到文件，可以避免无人读取管道时阻塞；该日志也应有容量管理。用户 stdout、stderr 已通过 Isolate 参数分别重定向。

worker 的外层看门狗应覆盖初始化、启动、运行和清理，处理挂起或取消。仅杀死 Java 启动的父进程，不能作为所有用户子进程已经退出的证明，必须通过沙箱清理确认任务被回收。

建议 worker 流程：

1. 从有限执行槽池分配 box-id，生成任务专用外部日志目录。
2. 初始化编译沙箱，写入固定名称的源文件。
3. 编译并检查退出码、meta 和编译产物。
4. 将所需产物复制到干净运行沙箱，为测试点准备输入。
5. 执行，解析资源和错误状态，交给可信比较器检查答案。
6. 保存结果，在 `finally` 中清理，确认完成后释放执行槽。

同一服务器的 box-id 不能冲突；不同服务器上的编号可以重复。编号范围和对应 UID 由 Isolate 配置决定。编译参数、路径和语言命令由服务端白名单生成，不能允许用户任意指定 Isolate 参数。

## 7 正式上线前的必要补充

- 编译环境与运行环境分离，每个测试点使用干净环境，避免前一测试点留下文件或修改程序。
- 标准答案和 checker 不暴露给用户代码。处理沙箱输出时防止符号链接等路径替换问题，自定义 checker 也应受限执行。
- 同时限制 stdout、stderr、工作目录总容量和文件数量，验证临时目录和共享内存不会绕过预期限制。
- 固定编译器、运行库、测试数据及 checker 版本，避免不同节点产生环境差异。
- Java 等运行时需要多线程和不同内存策略，不能直接套用 `--processes=1` 的 C++ 配置。
- 限制整个节点的并发量，给操作系统和 worker 留出资源；逐任务限制不能替代整机容量管理。
- 官方不建议默认在容器内运行 Isolate，因为 cgroup 委派等配置容易不完整；优先直接部署在独立 Linux 判题机或虚拟机中。
- 官方建议判题环境关闭 swap，检查 core dump 配置。系统调优应针对独立判题机执行，不要直接照搬到日常电脑。

## 8 常见问题

### 初始化或运行提示 cgroup 权限错误

确认正在使用与官方文档匹配的版本、cgroup v2 和已启动的 `isolate.service`。检查服务日志：

```bash
journalctl -u isolate.service -n 100 --no-pager
```

### 提示找不到程序或动态链接库

区分宿主机路径与沙箱路径。确认编译产物存在，具有执行权限，并核对默认目录映射、动态链接器和运行库是否可见。不要通过映射整个宿主机根目录解决。

### 编译器无法创建进程

编译阶段设置有限的 `--processes=N` 并启用 cgroup；保持初始化、运行、清理的 `--cg` 参数一致。具体数量需要针对工具链验证。

### 正常退出但没有 AC

Isolate 不比较答案。worker 应在外部根据题目规则调用比较器；特殊评测和浮点误差不能简单用字符串完全相等代替。

## 9 官方参考

- 项目与安装说明：https://github.com/ioi/isolate
- 命令参数与运行环境要求：https://www.ucw.cz/isolate/isolate.1.html

首次部署先用已知的正常程序、死循环、越界程序、超量输出和内存压力程序验证限制与分类，再开放用户提交。本文的命令演示基本操作，生产隔离配置与语言支持应结合实际机器验证。

## 10 接入本项目的实现

后端默认 `engine=isolate`，实现类为 `IsolateJudgeEngine`，配置在 `application.yaml` 的 `ioj.judge.isolate` 与 `ioj.judge.languages`。

| 环节 | 做法 |
| --- | --- |
| 编译 | 新建 box → 工作目录绑定为 `/box` → 写入源码 → `isolate --run -- javac/g++ ...` → 读取 meta 与编译输出 → `--cleanup` |
| 运行 | 每个测试点新建 box → 复制编译产物与 `input.txt` → `isolate --run -- 程序` → 读取 `time`/`cg-mem`/`status` → `--cleanup` |
| 目录 | `--dir=/box=<工作目录>:rw`；`extra-dirs` 中的目录按原路径只读绑定 |
| 限制 | `--time`、`--wall-time`、`--cg-mem`、`--fsize`、`--processes`（映射关系见 [03-judge.md](03-judge.md)） |
| 清理 | `finally` 中执行 `--cleanup` 并删除工作目录；失败写日志 |

Debian 13 + OpenJDK 21 下必须/建议的配置：

```yaml
ioj:
  judge:
    engine: isolate
    isolate:
      box-id-start: 1              # 避免 root 手工测试留下的 box 0
      compile-processes: 64        # javac 需要较多线程
      extra-dirs:
        - /etc/java-21-openjdk     # java.security，否则 JVM 启动失败
      resolve-executables: true    # 避开沙箱内不存在的 /etc/alternatives
    languages:
      JAVA:
        isolate-processes: 64      # JVM 线程数
        isolate-memory-extra-mb: 256
```

验证结果（Isolate 2.7，实际提交）：

| 场景 | 结果 |
| --- | --- |
| Java A+B | `ACCEPTED`，`timeUsedMs`/`memoryUsedKb` 有值 |
| Java 死循环 | `TIME_LIMIT_EXCEEDED` |
| Java 语法错误 | `COMPILATION_ERROR`（带 javac 输出） |
| C++ A+B | `ACCEPTED` |
| C++ 内存压力 | `MEMORY_LIMIT_EXCEEDED`（cgroup OOM） |
| C++ 超量输出 | `OUTPUT_LIMIT_EXCEEDED` |
| Python A+B | `ACCEPTED` |
| 判题完成后 | `/var/lib/isolate` 无残留 box |

> 权限说明：`/usr/bin/isolate` 通常为 `4750 root:isolate`。若运行后端的用户不在 `isolate` 组，会直接 `Permission denied`；
> 用 `sudo usermod -aG isolate <user>` 授权后需要重新登录。也可以由管理员调整二进制权限，但不建议放宽到所有用户。

