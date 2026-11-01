# Isolate 沙箱运维

本文只讲**机器侧**的事：Isolate 怎么装、权限怎么给、后端传了哪些参数、怎么手工复现一次评测、出问题怎么查。

- 判题状态机、引擎选择、meta → 状态的映射：见 [03-judge.md](03-judge.md)
- 整机部署、systemd、Nginx：见 [04-deployment.md](04-deployment.md)
- 接口契约（前端视角）：见 [../shared/api.md](../shared/api.md)

> 文中命令与输出均已在 **Debian 13 (trixie) + Isolate 2.7 + OpenJDK 21.0.11 + 内核 6.12.94** 上逐条执行（2026-10-30）。
> 换发行版请先核对 cgroup v2、内核版本与包来源。

## 1. 本项目如何调用 Isolate

后端**不链接任何 Isolate 库**，只通过命令行调用 `/usr/bin/isolate`。一次评测的生命周期：

```
从 box id 池取一个编号（默认 1..8）
├── 编译（仅编译型语言）
│   ├── isolate --init
│   ├── isolate --run -- <javac/g++/gcc ...>
│   └── isolate --cleanup
├── 判题器编译（仅当题目带 checker_code）
│   ├── isolate --init
│   ├── isolate --run -- g++ -o checker checker.cpp
│   └── isolate --cleanup
├── 每个测试点（全新 box、全新目录）
│   ├── isolate --init
│   ├── isolate --run -- <java/./main/python3>
│   ├── isolate --init（有判题器时再跑一次，目录不同）
│   ├── isolate --run -- /box/checker
│   └── isolate --cleanup
└── 归还 box id、删除临时工作目录
```

| 组件 | 职责 |
| --- | --- |
| `JudgeWorker` | 轮询待评测提交，用信号量限制并发，交给判题线程池 |
| `JudgeService` | 读提交 / 题目 / 测试点，调用引擎，落库 |
| `IsolateJudgeEngine` | 取 box、建工作目录、拼 `isolate` 命令行、解析 meta、判定状态 |
| `isolate` | 建命名空间、cgroup 限额、降权执行、输出 meta |

- 代码：`server/src/main/java/me/iynotuo/judge/judge/engine/IsolateJudgeEngine.java`
- 开关：`ioj.judge.engine=isolate`（默认，环境变量 `IOJ_JUDGE_ENGINE`）
- 创建引擎 Bean 时会做一次可执行性自检（见 §4.3）

## 2. 环境要求

| 项 | 要求 | 本机实测 |
| --- | --- | --- |
| 操作系统 | Debian 13 (trixie) amd64，使用官方 Isolate 包 | Debian 13 |
| cgroup | v2：`stat -fc %T /sys/fs/cgroup` 输出 `cgroup2fs` | `cgroup2fs` |
| 内核 | ≥ 5.19（`--cg-mem` 的内存统计依赖它） | `6.12.94+deb13-amd64` |
| Isolate | 2.7 | 2.7 |
| JDK | 21（判 Java 题，同时用于运行后端） | OpenJDK 21.0.11 |
| 工具链 | `g++`（C++17）、`gcc`（C11）、`python3` | 已安装 |
| 资源 | 至少能给编译器留出 1 核 + 512MB | 20 核 / 16GB |

Isolate 与宿主机**共享内核**，不是虚拟机级隔离：面向公网时应使用独立判题机、及时更新系统与 Isolate，
并且不要在该机器上存放数据库凭据或证书。详见 §11。

## 3. 安装

```bash
sudo apt update
sudo apt install -y curl ca-certificates openjdk-21-jdk g++ gcc python3
sudo install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://www.ucw.cz/isolate/debian/signing-key.asc \
    | sudo tee /etc/apt/keyrings/isolate.asc >/dev/null

sudo tee /etc/apt/sources.list.d/isolate.sources >/dev/null <<'EOF'
Types: deb
URIs: http://www.ucw.cz/isolate/debian/
Suites: trixie-isolate
Components: main
Architectures: amd64
Signed-By: /etc/apt/keyrings/isolate.asc
EOF

sudo apt update
sudo apt install -y isolate
sudo systemctl enable --now isolate.service
```

安装后三条自检，输出应与下面一致：

```console
$ isolate --version
The process isolator 2.7
(c) 2012--2026 Martin Mares and Bernard Blackham

$ isolate --check-config          # 无输出即通过
$ echo $?
0

$ systemctl is-active isolate.service
active
```

`isolate.service` 是一个常驻的 `isolate-cg-keep` 进程，唯一职责是**维持 cgroup 层级被委派**给 Isolate：
它把根 cgroup 路径写进 `/run/isolate/cgroup`，而 `/etc/isolate` 里是 `cg_root = auto:/run/isolate/cgroup`：

```console
$ cat /run/isolate/cgroup
/sys/fs/cgroup/isolate.slice/isolate.service
```

因此它**必须一直运行**。服务停掉后 `isolate --init` 会失败，症状是**所有提交都变成 `SYSTEM_ERROR`**，
但 `isolate --version` 依然正常——这类“看起来装好了却判不了题”的情况优先查这里。

## 4. 权限与目录

### 4.1 二进制权限

```console
$ ls -l /usr/bin/isolate
-rwsr-xr-x 1 root isolate 59824 Aug 24 06:43 /usr/bin/isolate
```

`-rwsr-xr-x` 即 **4755 + setuid root**：无论哪个用户执行，进程都以 root 建沙箱、再降权到隔离 UID。
所以 Debian 官方包**不要求**把运行用户加进 `isolate` 组——本机 `yuchenxi` 不在 `isolate` 组，
`isolate --cg --box-id=N --init` 依然成功。

| 打包方式 | 是否需要 `isolate` 组 |
| --- | --- |
| `4755 root:isolate`（官方包，setuid） | 不需要 |
| `4750` / `0750`（去掉 setuid，靠组授权） | 需要：`sudo usermod -aG isolate <user>`，**重新登录**后生效 |
| `/etc/isolate` 中 `restricted_init = 1` | 只有 root 能建 box，普通用户无法运行后端 |

统一把用户加进组是安全的兼容做法，对 setuid 包无副作用。

### 4.2 沙箱目录与 UID 区间

```console
$ isolate --cg --box-id=12 --init
/var/lib/isolate/12

$ ls -ld /var/lib/isolate/12 /var/lib/isolate/12/box
drwxr-xr-x 3 root     root     4096 /var/lib/isolate/12
drwx------ 2 yuchenxi yuchenxi 4096 /var/lib/isolate/12/box

$ grep '^isolate:' /etc/subuid /etc/subgid
/etc/subuid:isolate:165536:65536
/etc/subgid:isolate:165536:65536
```

| 位置 | 说明 |
| --- | --- |
| `/var/lib/isolate/<id>` | box 根目录，`--init` 打印的就是它（不是 `/box`）；属 root，由 Isolate 管理 |
| `/var/lib/isolate/<id>/box` | 沙箱内 `/box` 的宿主目录，归**调用者**，权限 `0700` |
| `/etc/subuid`、`/etc/subgid` | 隔离 UID 区间；沙箱内进程 UID 从这里分配（实测 `165543`） |
| `/tmp/ioj-isolate-*` | 本项目的工作目录（见 §4.3），通过绑定挂载给沙箱使用 |

沙箱内 UID 与宿主机用户不同，因此本项目把工作目录**放开为 `777`**，否则沙箱写不进去。
这只适用于“只放源码 / 输入 / 输出”的临时目录，不要往里面放敏感文件。

### 4.3 启动自检与工作目录

`IsolateJudgeEngine` 构造时检查 `ioj.judge.isolate.binary` 是否可执行，失败直接终止启动：

```text
isolate binary is not executable: /usr/bin/isolate.
Add the service user to the 'isolate' group and log in again: sudo usermod -aG isolate <user>
```

这是**有意的 fail-fast**：配置错了在启动时暴露，而不是等第一个提交失败。
只有二进制由外部机制延迟就绪（例如容器里启动后才挂载）时，才建议设 `check-on-startup: false`。

工作目录策略：

- 每次判题在 `/tmp` 下建 `ioj-isolate-XXXX`，编译产物放 `compile/`，每个测试点放 `run-<ordinal>/`；
- 测试点之间**复制**编译产物，前一个测试点无法污染后一个；
- `finally` 中删除整个目录并归还 box id，异常路径也不会泄漏。

## 5. 后端配置

`application.yaml` 中与沙箱相关的默认值（带 `${}` 的项可用环境变量覆盖）：

```yaml
ioj:
  judge:
    enabled: ${IOJ_JUDGE_ENABLED:true}
    engine: ${IOJ_JUDGE_ENGINE:isolate}
    concurrency: ${IOJ_JUDGE_CONCURRENCY:1}
    max-output-bytes: 16777216        # 16MB，同时决定 --fsize
    compile-timeout-ms: 30000
    checker:                          # 题目自带的 C++ 判题器（Special Judge）
      enabled: true
      time-limit-ms: 2000             # 独立于题限，见 §6.5
      memory-limit-mb: 512
      compile-timeout-ms: 30000
      compile-memory-mb: 512
      source-file: checker.cpp
      compile-command: g++ -O2 -std=c++17 -pipe -o checker checker.cpp
      message-limit-bytes: 1024
    isolate:
      binary: /usr/bin/isolate
      box-id-start: 1                 # 避开 root 手工测试留下的 box 0
      box-id-count: 8                 # 应不小于 concurrency
      check-on-startup: true
      compile-processes: 64           # javac 需要大量线程
      compile-time-seconds: 10
      compile-wall-time-seconds: 30
      compile-cg-mem-kb: 524288       # 编译期内存上限 512MB
      compile-fsize-kb: 16384
      wall-time-extra-seconds: 3
      setup-timeout-ms: 10000         # init/cleanup 的宿主侧超时
      extra-dirs:
        - /etc/java-21-openjdk        # 只读挂载，见 §6.3
      resolve-executables: true
    languages:
      JAVA:
        # 省略 display-name / source-file / run-wrapper；run-wrapper 只被 local 引擎使用
        compile-command: javac -encoding UTF-8 -d . Main.java
        run-command: java -Xmx{memoryMb}m -Xss64m -XX:+UseSerialGC Main
        time-multiplier: 2.0
        isolate-processes: 64
        isolate-memory-extra-mb: 256
```

| 配置 | 作用 | 调整建议 |
| --- | --- | --- |
| `engine` | 选择引擎；`local` 为无沙箱回退 | 生产固定 `isolate` |
| `concurrency` | 同时评测的提交数 | ≤ 核数 - 1，见 §9 |
| `box-id-start` / `box-id-count` | 本实例独占的 box 编号区间 | `count ≥ concurrency`；多实例分段 |
| `compile-processes` | 编译期 `--processes` | 保持 64，否则 javac 报 `pthread_create failed` |
| `compile-cg-mem-kb` | 编译期 `--cg-mem` | 512MB 起，编译大项目可上调 |
| `wall-time-extra-seconds` | 运行期墙钟 = CPU 限制 + 该值 | 死循环程序靠它兜底 |
| `extra-dirs` | 只读绑定进沙箱的宿主目录 | 见 §6.3 |
| `resolve-executables` | 把命令首词解析成真实路径 | 保持 `true` |
| `setup-timeout-ms` | init/cleanup 的宿主超时 | 机器很慢时上调 |
| `checker.time-limit-ms` / `checker.memory-limit-mb` | 判题器单次运行的 CPU / 内存 | 判题器要读整个输出，通常比题限宽；见 §6.5 |
| `checker.compile-command` | 判题器编译命令 | 必须产出名为 `checker` 的可执行文件 |

box id 是**阻塞式资源**：池里没有空闲编号时，判题线程会停在 `boxIds.take()` 等别人归还，
不会报错也不会丢任务。所以 `box-id-count` 小于并发时，表现是判题变慢，而不是失败。

## 6. 实际下发的命令

### 6.1 编译阶段

以 Java、题目 256MB 内存为例（`resolve-executables` 已把 `javac` 换成真实路径）：

```bash
/usr/bin/isolate --cg --box-id=1 \
  --processes=64 --time=10.00 --wall-time=30.00 \
  --cg-mem=524288 --fsize=16384 \
  --env=PATH=/usr/bin:/bin --env=TMPDIR=/box --env=LANG=C.UTF-8 \
  --dir=/box=/tmp/ioj-isolate-XXXX/compile:rw \
  --dir=/etc/java-21-openjdk=/etc/java-21-openjdk \
  --chdir=/box --stdin=/box/empty.txt \
  --stdout=/box/compile.out --stderr=/box/compile.err \
  --meta=/tmp/ioj-isolate-XXXX/compile.meta \
  --run -- /usr/lib/jvm/java-21-openjdk-amd64/bin/javac -encoding UTF-8 -d . Main.java
```

编译前，引擎会在 `compile/` 里写好 `Main.java`、`empty.txt`、`compile.out`、`compile.err` 四个文件。

### 6.2 运行阶段

题目 1000ms、Java 系数 2.0、内存 256MB：

```bash
/usr/bin/isolate --cg --box-id=1 \
  --processes=64 --time=2.00 --wall-time=5.00 \
  --cg-mem=524288 --fsize=16448 \
  --env=PATH=/usr/bin:/bin --env=TMPDIR=/box --env=LANG=C.UTF-8 \
  --dir=/box=/tmp/ioj-isolate-XXXX/run-1:rw \
  --dir=/etc/java-21-openjdk=/etc/java-21-openjdk \
  --chdir=/box --stdin=/box/input.txt \
  --stdout=/box/stdout.txt --stderr=/box/stderr.txt \
  --meta=/tmp/ioj-isolate-XXXX/run-1.meta \
  --run -- /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xmx256m -Xss64m -XX:+UseSerialGC Main
```

参数来源：

| 参数 | 计算方式 |
| --- | --- |
| `--time` | `max(100, ceil(题限 × 语言系数))` 毫秒 → 秒（两位小数） |
| `--wall-time` | `ceil(CPU 限制) + wall-time-extra-seconds` |
| `--cg-mem` | `max(16, 题限内存 + 语言额外内存) × 1024` KB |
| `--fsize` | `max(1024, max-output-bytes / 1024 + 64)` KB |
| `--processes` | 语言的 `isolate-processes`（下限 1） |
| `--dir=/box=<工作目录>:rw` | 工作目录绑定为可写 `/box` |
| `--dir=<extra-dirs>` | 同路径只读挂载（不带 `rw` 即只读） |
| `--meta` | 写在**沙箱外**的工作目录根下 |

### 6.3 五个容易踩的细节

1. **`--cg-mem` 必须配 `--cg`**。少了 `--cg` 时 isolate 直接拒绝：
   ```console
   $ isolate --box-id=8 --cg-mem=102400 ... --run -- /usr/bin/python3 main.py
   Options related to control groups require --cg to be set.
   $ echo $?
   2
   ```
2. **stdio 文件必须事先存在**。`--stdout` 指向不存在的文件时 isolate 不报错，而是**静默丢弃输出**
   （实测 rc=0、meta 正常），结果是“程序跑完了但输出为空”，容易被误判为 WA。引擎每次都先建空文件。
3. **`extra-dirs` 默认只读**。往里写会得到：
   ```text
   OSError: [Errno 30] Read-only file system: '/etc/java-21-openjdk/probe'
   ```
   需要写就加 `:rw`，本项目不需要。
4. **Debian 上 `/etc/java-21-openjdk` 是必需的**。JDK 里的 `conf/security/java.security` 只是一个软链：
   ```console
   $ readlink -f /usr/lib/jvm/java-21-openjdk-amd64/conf/security/java.security
   /etc/java-21-openjdk/security/java.security
   ```
   而沙箱默认**不挂载 `/etc`**（下节有挂载可见性实测），软链因此悬空，`javac` / `java` 一启动就失败：
   ```text
   Exception in thread "main" java.lang.InternalError: Error loading java.security file
       at java.base/java.security.Security.initialize(Security.java:105)
   ```
   `--dir=/etc/java-21-openjdk=/etc/java-21-openjdk`（只读、同路径）即可修复。
   换成 `java.security` 就在 JDK 目录内的发行版时，可以把这个配置留空。
5. **默认没有网络**。不加 `--share-net` 时沙箱内连外网直接失败：
   ```text
   BLOCKED: [Errno 101] Network is unreachable
   ```

### 6.4 沙箱内能看到的目录

沙箱只挂载固定的一组宿主目录，`/etc` 不在其中（在 box 内用 Python 探测 `os.path.exists` 的实测结果）：

| 路径 | 沙箱内可见 | 说明 |
| --- | --- | --- |
| `/usr`、`/lib`、`/bin` | ✅ | 默认只读挂载，编译器和解释器来自这里 |
| `/dev`、`/proc` | ✅ | 精简版设备与进程视图 |
| `/box` | ✅ | 本项目绑定为可写工作目录 |
| `/etc`、`/etc/alternatives` | ❌ | **默认不挂载**，所以 JDK 的 `java.security` 软链会悬空 |
| `/etc/java-21-openjdk` | ❌ | 由 `extra-dirs` 显式加回，见 §6.3 第 4 条 |

`extra-dirs` 的每一项都会被翻译成 `--dir=<路径>=<同一路径>`：

- **同路径**：JDK 内部用的是绝对路径软链，必须挂到相同位置才能解析；
- **只读**：不加 `:rw`，用户程序改不了宿主机文件；
- **按需**：不整目录挂 `/etc`，是为了只暴露运行时真正需要的那一小块。

### 6.5 判题器阶段（Special Judge）

题目带 `problems.checker_code` 时，每个测试点会多跑一次 isolate：先跑选手程序（6.2），再在**另一个目录**里跑判题器。

判题器目录（`/tmp/ioj-isolate-XXXX/check-1`，绑定为 `/box`）内容：

| 文件 | 来源 |
| --- | --- |
| `checker` | 判题器编译产物，权限 `rwxr-xr-x` |
| `input.txt` | 该测试点的标准输入 |
| `output.txt` | 选手输出文件的副本 |
| `answer.txt` | 该测试点的标准答案 |
| `stdout.txt` / `stderr.txt` / `empty.txt` | 预先建好的空文件（见 §6.3 第 2 条） |

```bash
/usr/bin/isolate --cg --box-id=1 \
  --processes=1 --time=2.00 --wall-time=5.00 \
  --cg-mem=524288 --fsize=16448 \
  --env=PATH=/usr/bin:/bin --env=TMPDIR=/box --env=LANG=C.UTF-8 \
  --dir=/box=/tmp/ioj-isolate-XXXX/check-1:rw \
  --dir=/etc/java-21-openjdk=/etc/java-21-openjdk \
  --chdir=/box --stdin=/box/empty.txt \
  --stdout=/box/stdout.txt --stderr=/box/stderr.txt \
  --meta=/tmp/ioj-isolate-XXXX/check-1.meta \
  --run -- /box/checker
```

与运行阶段的差别：

| 项 | 运行阶段 | 判题器阶段 |
| --- | --- | --- |
| 时间 | 题限 × 语言系数 | 固定的 `ioj.judge.checker.time-limit-ms` |
| 内存 | 题限 + 语言额外内存 | 固定的 `ioj.judge.checker.memory-limit-mb` |
| 进程数 | 语言的 `isolate-processes` | `1` |
| 被执行的程序 | `java` / `./main` / `python3` | `/box/checker`（已是沙箱内绝对路径，不做 PATH 解析） |
| 结论来源 | meta + 输出内容 | **退出码**（0/1/2/其他） |

> 为什么不用同一个目录：`answer.txt` 一旦出现在选手的工作目录里，选手程序可以直接读走标准答案；把 `checker` 放进去则可以被选手覆写。分两个目录是最省事且没有额外挂载参数的解法。

## 7. 手工验证（不经过后端）

用 box 5 复现一次完整评测。所有路径都要让沙箱用户能访问（读或写），否则会静默失败。

> 手工复现的坑：Isolate 的目录规则在**同一个 box 内跨 `--run` 保留**（同路径规则会被后一次覆盖）。
> 所以“这次没传 `--dir=/etc/java-21-openjdk` 居然也跑通了”，通常是上一次的规则还活着。
> 要复现后端的真实行为，先 `--cleanup` 再 `--init`。

```bash
# 1. 建 box，输出的是 box 根目录
isolate --cg --box-id=5 --init
# /var/lib/isolate/5

# 2. 准备“工作目录”，它以 /box 的身份出现在沙箱内
W=$(mktemp -d /tmp/isolate-demo.XXXX)
mkdir -p "$W/box"
: > "$W/box/stdout.txt"; : > "$W/box/stderr.txt"; : > "$W/box/empty.txt"
printf 'a, b = map(int, input().split())\nprint(a + b)\n' > "$W/box/main.py"
echo '1 2' > "$W/box/input.txt"
chmod -R 777 "$W"

# 3. 运行
isolate --cg --box-id=5 --processes=1 --time=2.00 --wall-time=5.00 \
  --cg-mem=102400 --fsize=1024 \
  --env=PATH=/usr/bin:/bin --env=TMPDIR=/box --env=LANG=C.UTF-8 \
  --dir=/box="$W/box":rw \
  --chdir=/box --stdin=/box/input.txt --stdout=/box/stdout.txt \
  --stderr=/box/stderr.txt --meta="$W/run.meta" \
  --run -- /usr/bin/python3 main.py

# 4. 看结果
cat "$W/box/stdout.txt"     # 3
cat "$W/run.meta"

# 5. 收尾
isolate --cg --box-id=5 --cleanup
rm -rf "$W"
```

一次正常运行的 meta（实测）：

```text
time:0.013
time-wall:0.014
max-rss:8428
csw-voluntary:11
csw-forced:2
cg-mem:3180
exitcode:0
```

| 字段 | 含义 | 本项目用法 |
| --- | --- | --- |
| `time` | CPU 时间（秒） | 写入 `timeUsedMs`（×1000 向上取整） |
| `time-wall` | 墙钟时间（秒） | 仅排障参考 |
| `max-rss` | 最大常驻内存（KB） | 仅排障参考 |
| `cg-mem` | cgroup 内存峰值（KB） | 写入 `memoryUsedKb`（为 0 时存 `null`） |
| `exitcode` | 退出码 | 非 0 → `RUNTIME_ERROR` |
| `exitsig` | 终止信号 | 用于生成失败原因 |
| `status` | `TO` / `RE` / `SG` / `XX` | 见 [03-judge.md](03-judge.md) §5.3 |
| `killed` / `cg-oom-killed` | 被墙钟 / 内存杀掉 | `cg-oom-killed` → `MEMORY_LIMIT_EXCEEDED` |

### 7.1 失败场景对照

| 场景 | 命令关键参数 | 实测输出 |
| --- | --- | --- |
| 死循环（TLE） | `--time=1 --wall-time=3` | `status:TO`、`message:Time limit exceeded`、`killed:1`、`time:1.097`，rc=1 |
| 申请 300MB、限额 64MB（MLE） | `--cg-mem=65536` | `cg-mem:65536`、`cg-oom-killed:1`、`exitsig:9`、`status:SG`，rc=1 |
| Java 用 `--processes=1` | `--processes=1` | JVM 起不来线程：`status:RE`、`exitcode:1`，stderr 可能为空 |
| 往只读挂载目录写 | `--dir=/etc/java-21-openjdk=...`（无 `rw`） | `OSError: [Errno 30] Read-only file system`，rc=1 |

Java 手工验证有两点要注意：编译必须 `--processes=64`；`javac` / `java` 建议写真实路径
（`/usr/lib/jvm/java-21-openjdk-amd64/bin/...`），否则沙箱内跟随 `/etc/alternatives` 软链会失败。

## 8. 通过后端验证

机器侧验证通过后，再确认后端真的能把提交送进沙箱（需要 MySQL 已就绪，见 [04-deployment.md](04-deployment.md)）。

```bash
cd server
export IOJ_DB_USER=ioj IOJ_DB_PASSWORD='...' IOJ_JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run        # 启动日志中不应出现 isolate 相关报错
```

```bash
BASE=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"admin123456"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 提交一次 A+B（Java），记下返回的提交 id
ID=$(curl -s -X POST $BASE/submissions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"problemId":1,"language":"JAVA","code":"public class Main{public static void main(String[] a){System.out.println(2);}}"}' \
  | sed -E 's/.*"id":([0-9]+).*/\1/')

# 轮询结果
curl -s $BASE/submissions/$ID -H "Authorization: Bearer $TOKEN"
```

判定成功的标志：

| 检查点 | 期望 |
| --- | --- |
| `status` | 从 `PENDING` → `JUDGING` → `ACCEPTED`（或预期的错误状态） |
| `timeUsedMs` | 非空，来自 meta 的 `time` |
| `memoryUsedKb` | **非空**，来自 meta 的 `cg-mem`（这是"真的跑在 Isolate 里"的直接证据） |
| 日志 | 无 `Isolate judging failed for submission`、无 `isolate --cleanup failed` |
| 宿主机 | `ls /var/lib/isolate` 在判题结束后应为空 |

如果 `memoryUsedKb` 一直是 `null`，先确认 `IOJ_JUDGE_ENGINE` 没有被设成 `local`：
回退引擎不读 cgroup，本来就没有内存统计。

## 9. 容量与调优

| 项 | 建议 | 原因 |
| --- | --- | --- |
| `concurrency` | 4–8（20 核机器） | 每个评测会占满 1 核跑用户程序，同时还要跑编译 |
| `box-id-count` | ≥ `concurrency` | 小于并发只会让判题排队，不会报错 |
| `compile-processes` | 64 不动 | `javac` 线程多 |
| `compile-cg-mem-kb` | 512MB 起 | 实测一个 `Main.java` 编译峰值约 64MB，大工程更高 |
| Java 的 `isolate-memory-extra-mb` | 256 | 实测空跑的 JVM `cg-mem` 就有 61–66MB，还要留 metaspace/线程栈 |
| 磁盘 | `/tmp` 与 `/var/lib/isolate` 同盘时留足空间 | 每次评测会复制产物到每个测试点目录 |

多实例（同一台机器跑两个判题进程）时 `box-id-start` 必须分段，例如 A 实例 `1..8`、B 实例 `9..16`；
重叠会互相抢同一个 box，表现为 `isolate --init` 失败或结果串台。跨机器则无需分段。

`--cg-mem` 是按**单次运行**限制的，不是整机配额：并发 8 且每题 512MB 时，峰值可能吃掉 4GB，
要给数据库和系统留出余量。

## 10. 排障

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动即退出，`isolate binary is not executable` | 路径不对，或运行用户没有执行权限（非 setuid 打包时） | 确认 `isolate --version` 可执行；必要时 `sudo usermod -aG isolate <user>` 后重新登录 |
| 所有提交都 `SYSTEM_ERROR`，日志有 `isolate --init failed for box N` | `isolate.service` 停了（cgroup 没委派），或 box 被别的用户占用 | `systemctl status isolate.service`；`isolate --cg --box-id=N --cleanup` 后再试 |
| `This box belongs to a different user` | 该编号被其他用户（常见是 root 手工测试）创建过 | 换 `box-id-start`，或让原用户执行 `--cleanup` |
| 手工调用报 `Options related to control groups require --cg to be set` | 参数里缺 `--cg` | 后端已默认带 `--cg`，只影响手工调试 |
| `status:XX` | Isolate 自身失败（box 状态异常、cgroup 写入失败） | 看日志里 isolate 的 stderr，清理 box 后重试 |
| `pthread_create failed` / Java 直接 `exitcode:1` | `--processes` 太小 | Java 的 `isolate-processes` 与 `compile-processes` 都用 64 |
| 编译报 `execve(...): No such file or directory` | 命令首词是 `/etc/alternatives` 软链，沙箱没挂载 | 保持 `resolve-executables: true` |
| JVM 报 `Error loading java.security file` | 沙箱默认不挂载 `/etc`，而 Debian 的 `conf/security/java.security` 是指向 `/etc/java-21-openjdk` 的软链 | `extra-dirs` 保留 `/etc/java-21-openjdk`；手工验证时先 `--cleanup` + `--init`，别让上一次的 `--dir` 残留 |
| 程序正常退出但输出为空 / 莫名 WA | `--stdout` 目标文件不存在，isolate 静默丢弃输出 | 复现时先 `: > stdout.txt`；后端已自动建空文件 |
| 写入目录报 `Read-only file system` | `--dir` 没加 `:rw` | 只有 `/box` 需要写；`extra-dirs` 保持只读 |
| 编译超时（`COMPILATION_ERROR: Compile Time Exceeded`） | `compile-time-seconds` / `compile-wall-time-seconds` 太小 | 分别上调 CPU 与墙钟限制 |
| 判题结束后 `/var/lib/isolate` 有残留目录 | 进程被强杀，没执行 `--cleanup` | 确认日志告警；用同一用户 `isolate --cg --box-id=N --cleanup` |
| 提交一直 `PENDING` | worker 被关闭（`IOJ_JUDGE_ENABLED=false`）或任务积压 | 查配置、`GET /api/v1/submissions?status=PENDING`、后端日志 |
| 判题正常但 `memoryUsedKb` 为 `null` | 引擎是 `local` 回退，或 meta 里没有 `cg-mem` | 确认 `IOJ_JUDGE_ENGINE=isolate`，并手工跑一次看 meta |

排查顺序建议：`isolate --version` → `systemctl is-active isolate.service` → `cat /run/isolate/cgroup`
→ 手工 §7 流程 → 再看后端日志。前两步就能覆盖大部分“装好了但判不了”的情况。

## 11. 安全边界

| 事实 | 含义 |
| --- | --- |
| 与宿主机共享内核 | 沙箱依赖命名空间 + cgroup + seccomp，内核漏洞可以逃逸；公网场景请用独立判题机并保持系统更新 |
| 二进制是 setuid root | 任何本机用户都能建沙箱；不要把判题机当作多租户共享机器 |
| 默认无网络（实测 `Errno 101`） | 用户程序无法外联，也拿不到数据库；除排障外不要加 `--share-net` |
| 沙箱 UID 来自 `/etc/subuid` | 与宿主机用户不同，因此工作目录被放开为 `777`；该目录只放源码/输入/输出 |
| 判题器是出题者上传的代码 | 同样是不可信代码，因此只在沙箱里编译与运行，独立时限；`answer.txt` 只出现在判题器目录，选手目录里没有（见 §6.5） |
| 资源限制是逐次的 | `--cg-mem`、`--time`、`--processes` 只约束单次运行，整机容量靠 `concurrency` 控制 |

其它约定：

- 后端**不要用 root 运行**；需要 root 的部分由 setuid 的 `isolate` 自己完成。
- 不要把数据库密码、JWT 密钥、证书放进判题机的工作目录或 `/tmp`。
- 提交的代码只允许在沙箱内编译与执行，宿主机上的 `javac`/`g++` 只作为沙箱内可执行文件存在。
- 上线前用 §7 的失败场景（TLE / MLE / RE / 只读挂载）各跑一遍，确认分类符合预期。
