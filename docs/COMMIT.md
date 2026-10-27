# 🔒 COMMIT.md - Git 提交与代码安全规范

为保证代码库的高质量维护与绝对安全，所有开发者与 AI Agent 在向代码库提交与推送代码前，**必须逐项执行本规范中的安全检查**。

---

## 1. 提交信息规范 (Conventional Commits)

每次提交信息必须具备明确的意图类型与清晰的中文摘要，格式如下：

```
<type>: <subject>
```

### 常用类型 (Type)
- `feat`: 新增系统优化 Hook 或 UI 新组件
- `fix`: 修复 Hook 异常、适配问题或 Bug
- `docs`: 文档完善（如 AGENTS.md、docs/ 下的设计规范）
- `refactor`: 重构代码结构或提升共享类复用性，不改变功能
- `test`: 增加或修改单元测试用例
- `chore`: 构建配置、Gradle 依赖或 CI/CD 流程调整

### 示例
```bash
git commit -m "feat: merge gallery copy hook and camera rename hook into better-coloros"
git commit -m "docs: establish harness engineering workflow and agent guidelines"
git commit -m "fix: resolve date pattern issue for motion photo MOD prefix"
```

---

## 2. 核心安全红线：严格禁止敏感信息提交

代码库将会被推送到公共或团队远程仓库中，**绝不允许任何敏感信息出现在 Git 追踪历史中**。

### 🚨 绝对禁止提交的内容清单：
1. **真实密钥与私钥**：
   - 生产环境签名私钥（`*.keystore`、`*.jks`、`*.pem`、`*.p12`、`id_rsa`）
   - 本项目 `app/signing/common.jks` 仅为使用开源密码 `androidcommonkey` 的通用公开测试密钥，专门用于供 GitHub Actions CI 构建出开箱即可安装测试的 Release APK。
2. **机密凭证与 Access Tokens**：
   - GitHub Personal Access Token (`ghp_...`)
   - 任何云服务 API Key（OpenAI, Baidu, Google Cloud, AWS 等）
   - 用户内网凭据或密码
3. **本地开发临时文件**：
   - `local.properties` (包含本地 SDK 路径)
   - `.idea/`、`.gradle/`、`build/`、`*.iml`
   - `.DS_Store`、系统临时缓存
4. **设备私有日志与个人照片**：
   - 包含个人身份信息的 logcat 抓取输出
   - 测试用的真实照片原图

---

## 3. 提交前自查清单 (Pre-commit Checklist)

在每次执行 `git commit` 与 `git push` 前，请务必执行以下核对流程：

```bash
# 1. 查看待暂存的文件清单，核实是否包含意外生成的临时文件
git status

# 2. 仔细检查暂存区的代码变动，确认无硬编码的密码、Token 或敏感路径
git diff --cached

# 3. 运行本地单元测试，确认核心逻辑全部通过
./gradlew test
```

---

## 4. 远程推送与 CI 触发流程

- 本仓库默认主分支为 **`main`**。
- 向 `main` 分支推送代码，或者推送形如 `v1.0.0` 的版本 Tag 时，GitHub Actions 会自动触发构建：
  1. 检出代码并配置 JDK 17 与 Gradle；
  2. 运行所有单元测试（`FilenameNormalizerTest` 等）；
  3. 编译 Debug 与 Release 两版 APK 并自动签名；
  4. 自动上传产物并在打 Tag 时发布 GitHub Release。
