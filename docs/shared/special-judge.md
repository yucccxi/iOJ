# 自定义判题器（Special Judge）使用指南

本文面向 iOJ 出题者，介绍何时需要自定义判题器、如何编写 Checker，以及如何测试和上传。Checker（也称 Special Judge，简称 SPJ）是一段 C++ 程序，用来判断选手输出是否符合题目要求。

## 什么时候需要 Checker

如果题目只有唯一输出，且只需按文本比较答案，通常不需要 Checker。iOJ 的默认比较会忽略每行末尾的空格和制表符、输出末尾的空行，以及不同的换行格式；其他内容仍需一致。

以下情况适合使用 Checker：

- 一个测试点存在多个不同但都正确的答案；
- 需要按题目规定的误差判断浮点数；
- 选手需要输出任意一个合法方案，例如路径、匹配或调度方案；
- 需要检查输出满足某些约束，而不是与唯一标准答案逐字相同。

## Checker 能读取什么

每次检查一个测试点时，Checker 的当前目录中会提供三个 UTF-8 文本文件：

| 文件名 | 内容 |
| --- | --- |
| `input.txt` | 当前测试点的输入数据 |
| `output.txt` | 选手程序的输出 |
| `answer.txt` | 该测试点的标准答案 |

Checker 不需要命令行参数。请在程序中按文件名读取所需数据。`input.txt` 可帮助理解测试点或验证输出约束；`answer.txt` 可用于参考，但多解题不应要求选手输出与它完全相同。

**标准输入（stdin）是空的**，请不要用 `std::cin` / `scanf` 读取数据，一律按上面的文件名打开文件。判题器自己的 stdout 会被当作"失败说明"保存下来（最多 1024 字符），stderr 只用于自身故障的诊断。

## 如何返回判定结果

Checker 通过退出码返回判定：

| 退出码 | 含义 |
| ---: | --- |
| `0` | 当前测试点通过 |
| `1` | 答案错误 |
| `2` | 格式错误（Presentation Error），按答案错误处理 |
| 其他 | Checker 本身出错，属于评测问题而非选手答案错误 |

答案错误时，可以将简短原因写到标准输出（stdout），作为该测试点的说明。例如：`output is not sorted`。Checker 自身出错时，可将诊断信息写到标准错误（stderr）。不要在消息中输出隐藏测试数据或完整标准答案。

如何区分 `1` 和 `2`：**答案内容本身不对**（数值不同、方案不合法）返回 `1`；**答案能看懂但格式不合要求**（缺少数值、多输出内容、大小写或分隔符不符）返回 `2`。两者在提交结果里都显示为 `WRONG_ANSWER`，区别只在说明文字：你写到 stdout 的内容会原样展示；返回 `2` 但没有输出任何说明时，平台会自动填 `Presentation Error`。

| Checker 退出码 | 写入 stdout | 提交结果 `status` | 该测试点的说明 `message` |
| --- | --- | --- | --- |
| `1` | `the integer is incorrect` | `WRONG_ANSWER` | `the integer is incorrect` |
| `2` | `unexpected extra output` | `WRONG_ANSWER` | `unexpected extra output` |
| `2` | （不输出） | `WRONG_ANSWER` | `Presentation Error` |

例如，正确答案返回 `0`，答案错误返回 `1`，格式问题返回 `2`，Checker 自身故障返回 `3`：

```cpp
#include <fstream>
#include <iostream>
#include <string>

int main() {
    std::ifstream input("input.txt");
    std::ifstream output("output.txt");
    std::ifstream answer("answer.txt");

    if (!input || !output || !answer) {
        std::cerr << "checker: failed to open a required file\n";
        return 3; // Checker 自身出错
    }

    long long contestantValue;
    long long expectedValue;
    if (!(answer >> expectedValue)) {
        std::cerr << "checker: answer.txt does not start with an integer\n";
        return 3; // 标准答案格式不对，属于 Checker/数据问题
    }
    if (!(output >> contestantValue)) {
        std::cout << "output is missing the required integer\n";
        return 2; // 格式错误：没有输出整数
    }

    if (contestantValue != expectedValue) {
        std::cout << "the integer is incorrect\n";
        return 1; // 答案错误
    }

    // 若题目不允许额外内容，应检查是否还有多余 token。
    std::string extra;
    if (output >> extra) {
        std::cout << "unexpected extra output\n";
        return 2; // 格式错误：多输出了内容
    }

    return 0; // 通过
}
```

示例里的四条分支正好对应四种退出码：`0` 通过、`1` 答案错误、`2` 格式错误、`3`（以及任何其他非 0/1/2 的值）表示 Checker 自身出错。**不要把"答案错"和"Checker 出错"混用**：如果所有失败都返回 `1`，一次写错的 Checker 会让所有选手都收到"答案错误"，问题会被埋掉。

以上仅是单整数答案的示例。请根据题目要求编写实际校验逻辑，例如验证方案是否合法、处理多种正确答案或按规定比较浮点误差。

## 运行环境与限制

Checker 在评测沙箱里运行，和选手程序**分属两个不同的目录与进程**：选手程序先跑完并退出，之后才轮到 Checker，所以 Checker 读取的 `output.txt` 不会被选手再次改写，`answer.txt` 也不会出现在选手程序的目录里。

| 项 | 默认值 | 说明 |
| --- | --- | --- |
| CPU 时间 | 2000 ms | 由平台的 `ioj.judge.checker.time-limit-ms` 配置，**与题目时限无关** |
| 内存 | 512 MB | 由 `ioj.judge.checker.memory-limit-mb` 配置 |
| 编译 | 每条提交编译一次 | 失败会判 `CHECKER_ERROR`，`g++` 的报错记录在提交详情的评测信息里 |
| 网络 | 无 | 沙箱内不能访问外网，也不要依赖任何本地文件 |

由此带来两条实践规则：

1. Checker 超时或内存超限会判 `CHECKER_ERROR`（评测侧问题），**不会**算成选手的 TLE / MLE。所以 Checker 必须足够快、足够省内存。
2. Checker 只在 `engine=isolate` 的部署上执行。若平台使用 `local` 引擎（无沙箱的开发机或 CI），带 Checker 的题目会统一返回 `CHECKER_ERROR`，这属于环境限制而非你的代码问题。

## 编写建议

1. **完整检查选手输出。** 不要只检查答案前缀；按题目要求决定是否允许多余内容、空白或特定格式。
2. **多解题检查合法性。** 验证输出是否满足题目约束，不要简单地与某一份标准答案比较。
3. **浮点数使用误差规则。** 按题目说明采用绝对误差或相对误差，不要用浮点数 `==` 判断近似相等。
4. **区分答案错误和 Checker 故障。** 选手输出不正确时返回 `1` 或 `2`；Checker 无法读取文件、解析内部数据异常等自身故障时返回其他退出码，并将诊断写入 stderr。
5. **给出简短清楚的说明。** stdout 中的失败说明可能展示给选手，避免包含隐藏数据或过多内部信息。
6. **使用合理的算法。** Checker 应能在题目数据规模下及时完成；请避免不必要的高耗时或高内存处理。

## 本地测试

上传前，建议在本地准备一组 `input.txt`、`output.txt` 和 `answer.txt`，分别测试：

- 正确输出；
- 错误输出；
- 缺少内容、格式错误或多余内容；
- 边界数据以及题目特有的合法方案；
- 浮点误差边界（如果题目涉及浮点数）。

Checker 使用 C++17。可在本地用以下命令编译并运行：

```bash
g++ -O2 -std=c++17 -pipe -o checker checker.cpp
./checker
printf 'exit code: %s\n' "$?"
```

确认程序对不同用例返回了预期退出码，并检查 stdout/stderr 是否合适。提交一份使用 Checker 的题目后，也应实际提交正确和错误的参赛程序验证判题结果。

## 上传和管理 Checker

Checker 是题目设置的一部分，需要由题目作者或管理员管理。它和题面、测试数据是**互相独立**的两套设置：编辑题面或测试数据都不会清空 Checker，反之亦然。

查看、上传、清空 Checker 分别使用：

| 操作 | 接口 |
| --- | --- |
| 查看源码 | `GET /problems/{id}/checker`（未配置时返回空字符串） |
| 上传 / 替换 | `PUT /problems/{id}/checker`，请求体 `{ "code": "..." }` |
| 清空 | `PUT /problems/{id}/checker`，请求体 `{ "code": "" }` |

两个接口都只对题目作者与管理员开放；当前预览版前端还没有对应的管理界面，可以直接调用接口（字段与错误码见 [api/04-problems.md](api/04-problems.md) 的 4.7 节）。上传前请先在本地编译并测试。清空 Checker 后，该题会恢复使用默认文本比较。

更换测试数据后建议复查一遍 Checker：`answer.txt` 的内容会随测试数据一起变化，依赖标准答案的 Checker 需要重新验证。

## 遇到问题时

如果提交结果显示 `CHECKER_ERROR`，通常表示 Checker 没有正常完成判定，而不是选手答案错误。请检查源码是否能用 C++17 编译、三份文件名是否正确、程序是否能处理当前测试数据，以及所有路径上的退出码是否符合约定。也可以用本地样例复现后，再联系平台维护者协助排查。
