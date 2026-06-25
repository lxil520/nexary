# 发布清单

这页是公开发布用的操作清单。示例版本使用 `0.12.0`，实际发布 patch 版本时把命令里的版本号一并替换。

## 发布前检查

发布前至少完成这些检查：

- `scripts/release/preflight.sh 0.12.0`
- `./gradlew check`
- `./gradlew verifyReleaseGate`
- `./gradlew publishToMavenLocal`
- 按“本地生成 Central bundle”中的完整命令运行 `./gradlew mavenCentralBundle -PnexaryVersion=0.12.0`，并确认 `build/distributions/nexary-0.12.0-central-bundle.zip` 生成
- GitHub Actions 里的 build、dependency review 和 secret scan 通过
- POM 中的 `group`、artifact 名称、license、developer、SCM 信息正确
- sources jar、Javadoc jar、`.pom`、`.module`、签名文件都在 Central bundle 中
- 仓库中没有真实凭证、私有端点或私有仓库配置
- `nexary-samples` 只作为源码样例和本地验证工程，不进入 Central 发布模块

## Maven Central 前置条件

发布到 Maven Central 前，先确认这些外部条件已经完成：

- Sonatype Central Portal 中已验证 `com.aweimao` namespace。
- PGP 公钥已经发布到 Sonatype Central validation 能访问的 keyserver，且私钥只以 GitHub secret 或本地临时环境变量形式使用。
- Central Portal token 已创建；GitHub secrets 使用 token 的 username 和 password，不使用个人登录密码。
- GitHub 仓库地址、SCM 地址和开发者连接地址指向 `lxil520/nexary`。

需要的 GitHub variables：

- `NEXARY_PROJECT_WEBSITE=https://github.com/lxil520/nexary`
- `NEXARY_PROJECT_SCM_URL=https://github.com/lxil520/nexary.git`
- `NEXARY_PROJECT_SCM_CONNECTION=scm:git:https://github.com/lxil520/nexary.git`
- `NEXARY_PROJECT_SCM_DEVELOPER_CONNECTION=scm:git:ssh://git@github.com:lxil520/nexary.git`

需要的 GitHub secrets：

- `NEXARY_SIGNING_KEY`
- `NEXARY_SIGNING_PASSWORD`
- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`

## 本地生成 Central bundle

本地只生成上传包，不会发布到 Central：

```bash
./gradlew mavenCentralBundle \
  -PnexaryVersion=0.12.0 \
  -PprojectWebsite=https://github.com/lxil520/nexary \
  -PprojectScmUrl=https://github.com/lxil520/nexary.git \
  -PprojectScmConnection=scm:git:https://github.com/lxil520/nexary.git \
  -PprojectScmDeveloperConnection=scm:git:ssh://git@github.com:lxil520/nexary.git \
  -PnexarySigningKey="$NEXARY_SIGNING_KEY" \
  -PnexarySigningPassword="$NEXARY_SIGNING_PASSWORD"
```

生成后检查：

```bash
unzip -l build/distributions/nexary-0.12.0-central-bundle.zip | grep 'nexary-bom/0.12.0'
unzip -l build/distributions/nexary-0.12.0-central-bundle.zip | grep '.asc'
unzip -l build/distributions/nexary-0.12.0-central-bundle.zip | grep '.sha1'
```

## GitHub Actions 发布

推荐用 tag 触发正式发布：

```bash
git tag v0.12.0
git push origin v0.12.0
```

`release.yml` 会从 tag 对应的 commit 生成 Central Portal bundle，并上传为 GitHub Actions artifact。只要 Central secrets 存在，tag 触发会继续上传并发布 Central deployment。

发布 run 创建后，用脚本查看摘要，不要靠浏览器日志页反复刷新：

```bash
scripts/release/watch-github-run.sh <run-id>
```

如果 Central token 缺失，发布步骤会失败，不会把缺少 Central 发布的 tag run 标成成功。需要只验证 bundle 时，使用 `workflow_dispatch`，填入 `0.12.0` 或 `v0.12.0`，并保持 `publish_to_central=false`。手动发布到 Central 必须从已存在的 tag ref 运行，且输入版本必须匹配选中的 tag；不要从 `main` 分支手动发布 Central deployment。

## Maven Central 发布后检查

Central Portal 显示 published 后，再检查 Maven Central 是否同步：

```bash
curl -I https://repo.maven.apache.org/maven2/com/aweimao/nexary-bom/0.12.0/nexary-bom-0.12.0.pom
curl -I https://repo.maven.apache.org/maven2/com/aweimao/nexary-framework/nexary-core/0.12.0/nexary-core-0.12.0.jar
```

也可以直接运行：

```bash
scripts/release/check-central.sh 0.12.0
```

同步完成后再更新 GitHub Release 说明和 README 中的版本选择提示。不要在 Central 还没有同步时告诉用户直接复制 Maven Central 版本。

## 失败处理

- 本地验证失败：修复后重新运行本地命令，不创建 tag。
- bundle 生成失败：先检查 Gradle 版本、JDK 21、签名 secret、SCM metadata 和 Central 模块列表。
- Central validation 失败且未发布：在 Central Portal 删除或放弃该 deployment，修复后重新生成 bundle；如果 tag 指向错误 commit，先删除本地和远端 tag，再在正确 commit 上重建 tag。
- Central 已发布但发现问题：Maven Central 上的已发布版本不能回滚或复用；修复后发布新的 patch 版本，并在 GitHub Release 中说明受影响版本。
- GitHub Actions 卡在 Central 状态查询：先在 Central Portal 查看 deployment 状态，再决定是否重新运行 workflow。不要在无法确认 Central 状态时重复推同一个 tag。

## 当前支持声明

`0.5.x` 只声明已经通过 gate 的组合。Spring Boot 2.7 / JDK 8+、Spring Boot 3.3 / Java 17+、Spring Boot 4.1 / Java 21 的说明以 README 和各能力文档为准；未验证 provider 不写成已支持。
