# Map Sug 拼写纠错（召回 + 排序）

面向海外多国家、多语言地图搜索建议（Sug）的实时纠错服务，纯 Java 17 实现，运行时零第三方依赖。
当前内置 **墨西哥 MX（西语）/ 英国 GB（英语）/ 马来西亚 MY（马来语）/ 印度尼西亚 ID（印尼语）**。

核心约束：

* **输入未完成**：用户还在打字，最后一个词可能只是前缀。纠错只修拼写、不做补全，最后一个词的输出长度与输入对齐：
  `aple → appl`、`starbucks coefe → starbucks coffe`、`Ecatepec de Moaalos → Ecatepec de Morelos`；
  合法前缀（`appl`、`ecatepec de mor`）保持不变。末尾带空格表示最后一个词已输完，按完整词纠错。
* **时延 < 50 ms**：单次纠错（不走缓存）p99 约 0.02–0.1 ms，p999 < 0.2 ms；内部 30 ms 预算，超时降级为原样返回。

---

## 快速开始

```bash
./run.sh gen-sample                     # 生成四张表的样例数据 -> data/sample/（已提交，可跳过）
./run.sh train                          # 训练排序模型 + 调参 + 评测 -> model/<CC>.properties, model/report.txt
./run.sh correct --country MX "Ecatepec de Moaalos" "starbucks coefe"
./run.sh correct --country GB --debug true "aple"        # 打印每个 token 的候选与打分
./run.sh bench                          # 无缓存时延压测
./run.sh serve --port 18765             # HTTP 服务 + 演示页 http://127.0.0.1:18765/
mvn test                                # 单元测试
```

需要 JDK 17+ 与 Maven 3.8+。`run.sh` 会在源码变更时自动重新打包。

HTTP 接口：

```
GET /api/correct?country=MX&q=ecatepec%20de%20moaalos
GET /api/countries
GET /health
```

```json
{"country":"MX","original":"Ecatepec de Moaalos","corrected":"Ecatepec de Morelos","changed":true,
 "confidence":1.0,"latencyMicros":180,"cached":false,"degraded":false,
 "tokens":[...,{"original":"Moaalos","corrected":"Morelos","complete":false,"changed":true,"sources":"[FUZZY_PREFIX]","candidates":3}]}
```

---

## 整体架构

```
query ─► 归一化/分词 ─► 门控 ─► 多路召回 ─► 特征 + 对数线性打分 ─► Beam 解码(二元语言模型上下文) ─► 接受判定 ─► 输出
          (折叠变音符,     (保护词/数字/     (已知纠错表        (13 维 token 特征,          (路径分 = Σtoken分        (最优路径比原 query
           记录"最后一词     高频词/高频前缀    SymSpell           Listwise Softmax 训练)       + λ·Σ bigram gain)        高出阈值才改写)
           是否已完成")      直接放行)         模糊前缀 Trie
                                              音近键
                                              缺空格切分)
```

### 1. 归一化与分词（`text/`）
* `TextNormalizer.fold`：小写、去变音符（`Juárez → juarez`）、去撇号（`Sainsbury's → sainsburys`）。所有索引都用折叠后的 key，展示时从词典取最高频的原始写法恢复重音，并保留用户的大小写风格。
* `Tokenizer`：保留每个词在原串中的偏移，改写时只替换词本身，用户的空格、逗号原样保留；最后一个词若后面没有分隔符，标记为 **未完成**。

### 2. 门控（`RecallEngine.gated`）
以下 token 不进入召回，直接保留：国家保护词与地址缩写（`de / col / av / jl / st …`）、含数字的 token（门牌号、邮编）、过短的 token、
**完整且高频**的词、**未完成且是高频前缀**的词，以及长度 ≤4 且有一定证据的短前缀（如 `izt` → Iztacalco）。
这一步保证合法前缀不被误改，同时让大部分 token 的处理开销降为 O(1)。

### 3. 多路召回（`recall/`）

| 通道 | 适用 | 说明 |
|---|---|---|
| `KnownCorrectionRecaller` | 全部 | 清洗后三元组中高频、占比高的 `wrong → right`，高精度 |
| `SymSpellRecaller` | 完整词 | 对称删除索引，编辑距离 ≤2（短词 ≤1），查询开销与词表大小无关 |
| `FuzzyPrefixRecaller` | 未完成词 | 在前缀 Trie 上做带 OSA 动态规划行的 DFS 剪枝搜索，命中路径**截断到输入长度**：`aple` 命中 `apple`（1 次编辑）输出 `appl` |
| `PhoneticRecaller` | 完整词 | 分语言音近键：西语 b/v、s/z/c、ll/y、不发音 h；英语 ph/f、ck/k；印尼/马来旧拼写 oe/u、dj/j、tj/c |
| `SplitRecaller` | 全部 | 缺空格：`starbuckscoffee → starbucks coffee`，未完成时右半部分只需是合法前缀 |

各通道结果按输出 key 合并（保留全部来源），再用先验（已知纠错 > 编辑距离 > 频次）截断到每个 token 最多 24 个候选。

### 4. 排序（`rank/`）
token 级特征（`FeatureExtractor`）：是否原词、编辑距离、相对编辑距离、**噪声信道对数概率**（从三元组对齐学到的字符级替换/插入/删除/换位分布，相邻键与重复字母有更高先验）、
对数频次（未完成词用前缀频次）、相对原词的频次增益、已知纠错支持度、音近匹配、首字母一致、长度差、是否切分、原词是否未登录、是否前缀候选。

对数线性模型，**Listwise Softmax 交叉熵 + AdaGrad + L2** 训练（`RankerTrainer`）：
* 正例：训练集三元组的 wrong token，标签是 right；未完成 / 完整根据 right 是否为完整词判断；
* 负例：训练 query 中没有被对齐出错误的 token，标签是“保持原样”；
* 对一半正例随机置零“已知纠错”特征，避免模型只会记忆已见过的错误、对新错误泛化差。

### 5. 上下文解码与接受判定（`decode/BeamDecoder`）
路径分 = Σ token 分 + λ · Σ `gain(prev, cur)`，其中 `gain = log P(cur|prev) − log P(cur)`（stupid backoff 二元模型，语料是 Google Maps Top1 名称/地址和点击结果）。
未完成词用前缀版本 `P(prefix|prev)`。所以 `de moaalos` 会因为 `de morelos` 常见而被拉向 `morelos`。
只有最优路径比“全部保持原样”的路径高出 `accept_threshold` 才改写。λ 和阈值在 dev 集上以 **F0.5**（更看重精度，错改比漏改更伤 Sug）网格搜索得到。

### 6. 在线工程
* `SpellCorrector` 线程安全，所有索引在构建后只读；LRU 缓存（Sug 中连续按键产生大量重复前缀）。
* 时间预算：超过 `timeBudgetMillis` 后剩余 token 只保留原词，结果标记 `degraded=true`，保证不拖垮整条链路。
* `SpellService` 按国家路由；服务启动时预热 JIT。

---

## 四张数据表如何使用

数据目录中放四个 TSV 文件（带表头，按列名读取，列顺序无关，多余列忽略）。直接替换 `data/sample/` 或用 `--data` 指向你的目录即可。

| 文件 | 列 | 在系统中的用途 |
|---|---|---|
| `query_top1.tsv` | `country query top1_name [top1_address]` | Top1 名称/地址的词并入词表并构建二元语言模型；用来对齐出 **query 级评测参考答案**；为训练提供“保持原样”的负例 |
| `correction_triples.tsv` | `country wrong_token right_token edit_distance [count]` | 清洗后训练噪声信道错误模型、构建已知纠错表、作为排序模型正例；同时用于 token 级评测 |
| `token_stats.tsv` | `country token df` | 主词表与频次；前缀 Trie 的子树频次用于判断“合法前缀”；SymSpell 与音近索引都建在这上面 |
| `query_click.tsv` | `country query clicked_name [clicks]` | 点击结果按点击量加权进入二元语言模型和词表 |

注意：`query` 列 **不做 trim**，末尾空格表示最后一个词已完成，这是区分“前缀”和“完整词”的唯一信号。

### Ground Truth 去噪（`TripleCleaner`）
三元组是用 Google Maps Top1 对齐出来的，Top1 不一定是用户想找的地点，所以“错误 token”经常其实是合法词。以下情况会被丢弃：
两边折叠后相同、含数字、编辑距离对长度来说过大、right 既不是已知词也不是已知前缀、wrong 本身是高频词且不比 right 罕见多少。
丢弃原因会在训练报告的 `dropped={...}` 中统计。评测时同样跳过 query 与 Top1 基本对不上（对齐比例 < 50%）的样本。

---

## 评测

`./run.sh train` 对每个国家：三元组按 wrong token 哈希、query 按文本哈希切分为 80% 训练 / 10% dev / 10% test；词表等统计用全量数据，错误模型和已知纠错表只用训练集三元组。

* **query 级**：参考答案 = 原 query 应用（经同样去噪规则过滤的）对齐修正；整句折叠后完全相等才算对，因此把前缀补全成完整词（`appl → apple`）算错。
  指标：precision、recall、F0.5、误纠率 FPR（本不需要纠错却被改写）、`overCompletion`（最后一个未完成词输出比输入长），以及端到端时延分位数。
* **token 级**：测试集三元组的召回覆盖率（right 是否在候选中）与 top1 准确率（不带上下文）。

样例数据上的结果（`model/report.txt`，模型训练后 vs 默认权重基线）：

| 国家 | precision | recall | F0.5 | FPR | 补全越界 | p99 时延 |
|---|---|---|---|---|---|---|
| MX | 0.966 | 0.988 | 0.970 | 5.3% | 0 | 79 µs |
| GB | 0.966 | 0.980 | 0.969 | 3.4% | 0 | 92 µs |
| MY | 0.987 | 0.938 | 0.977 | 1.5% | 0 | 83 µs |
| ID | 0.985 | 0.952 | 0.978 | 0.9% | 0 | 60 µs |

`model/errors_<CC>.tsv` 列出测试集中所有不一致样本。人工看下来，相当一部分“误纠”其实改对了，只是参考答案没有修：
要么 Top1 是另一个地点但地址里恰好有同一个词（如 `westminster abey` 的 Top1 是另一个 Westminster 地点），要么错误超出了对齐脚本的编辑距离上限（如 `Jalwn Sdirmqn → Jalan Sudirma`）。
所以表中 FPR 偏保守。样例数据是合成的，**数字只用于验证流程能跑通，不代表线上效果**；换成真实数据后重新 `train` 即可得到真实指标。

---

## 接入真实数据与上线建议

1. 把四张表导出为上述 TSV（可只保留目标国家），执行 `./run.sh train --data <dir> --model <dir>`，查看 `report.txt` 和 `errors_*.tsv`。
2. `serve --data <dir> --model <dir>` 加载同一份数据和模型；已训练模型会自动从 `<model>/<CC>.properties` 读取，缺失时使用默认权重。
3. 词表规模到百万级时：
   * `SpellConfig.trustFreq` 建议按真实分布显式设置（默认取词表频次 75 分位数，下限 20），也可用 `--trust-freq` 覆盖；
   * `dictMinFreq` 提高到能滤掉长尾错拼的程度；
   * SymSpell 的 `prefixLength=7` 已限制内存；如需秒级启动，可把 `CountryResources` 序列化为离线产物（当前在启动时从 TSV 构建）。
4. 新增国家：在 `CountryProfile` 加一项（语言、保护词），必要时在 `Phonetic` 增加该语言的音近规则，数据中带上对应 `country` 即可。

---

## 代码结构

```
src/main/java/com/mapsug/spell/
  text/      归一化、分词、OSA 编辑距离与对齐、键盘邻接、分语言音近键
  dict/      词典 + 前缀 Trie（模糊前缀搜索）、SymSpell、二元语言模型、已知纠错表、噪声信道错误模型
  data/      四张表的读写、三元组对齐抽取、Ground Truth 去噪
  recall/    候选结构、各召回通道、门控与合并截断
  rank/      特征、对数线性模型（可持久化）、Listwise 训练器
  decode/    Beam 解码
  core/      国家配置、资源构建、SpellCorrector（在线入口）、SpellService（多国家路由）
  pipeline/  数据切分、训练/调参流水线、评测
  sample/    样例数据生成器（模拟 Sug 打字：截断、键盘/音近/漏字/换位错误、3% 不相关 Top1）
  server/    JDK 内置 HttpServer 接口
  cli/       命令行入口
src/main/resources/web/index.html   演示页
```
