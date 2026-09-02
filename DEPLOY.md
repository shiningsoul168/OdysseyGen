# OdysseyGen 部署指南

> 环境：2C2G 阿里云（2 核 2G），MySQL 8 + Redis 直接运行在宿主机，应用同机部署。
> 两种方式任选：**A. 传统 jar 部署**（现状） / **B. Docker 容器化**（✅ 2026-09 已上线，见"实际部署踩坑记录"）。
> 线上现状：后端 Docker 容器（host 网络，mem 768m）+ nginx 托管前端（/opt/dist）。

---

## ⚠️ 实际部署踩坑记录（2026-09 容器化实录，面试可讲）

> 这些坑都是真实踩过并解决的，按"现象 → 原因 → 解法"记录。面试讲容器化部署时，主动说其中 2-3 个，比背 Dockerfile 命令有说服力得多。

### 坑 1：`get.docker.com` 安装脚本不识别 Alibaba Cloud Linux
- **现象**：`curl -fsSL https://get.docker.com | sh` 报 `ERROR: Unsupported distribution 'alinux'`
- **原因**：官方脚本的发行版白名单不含 alinux
- **解法**：改用阿里云 docker-ce 镜像仓库手动装：
  ```bash
  dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
  sed -i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo   # alinux3 兼容 CentOS 8 包
  rpm --import https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
  dnf install -y --nogpgcheck docker-ce docker-ce-cli containerd.io docker-compose-plugin
  ```

### 坑 2：Docker 镜像加速器选错导致 `docker pull` 卡死/极慢
- **现象**：`docker pull eclipse-temurin:17-jre` 卡住或 8MB 下半天
- **原因**：配置了已停止服务的镜像源（`dockerproxy.net` 2024 年已关停）；daocloud 系列在部分地域慢
- **解法**：先探测哪些源真的可达，再排序：
  ```bash
  for m in docker.1ms.run docker.m.daocloud.io m.daocloud.io docker.1panel.live hub.rat.dev; do
    printf '%-22s' "$m"; timeout 10 curl -sI "https://$m/v2/" | head -1 || echo FAIL
  done
  # 200/401 = 可达；改 /etc/docker/daemon.json 把最快的放最前，systemctl restart docker
  ```
- **经验**：国内服务器配镜像加速别只抄网上的源列表——**先测再用**；已完成层会缓存，换源后重拉不重头下载。

### 坑 3：scp 上传目录产生嵌套子目录
- **现象**：`scp -r 本地dist root@ip:/opt/dist` 后，文件落在 `/opt/dist/本地目录名/` 里，nginx 403
- **原因**：scp 拷贝目录时会以"目标路径 + 目录名"落盘
- **解法**：用 tar 打包后单文件上传再解压（结构 100% 可控）：
  ```bash
  tar -czf dist.tar.gz -C dist .
  scp dist.tar.gz root@ip:/tmp/
  ssh root@ip "mkdir -p /opt/dist.new && tar xzf /tmp/dist.tar.gz -C /opt/dist.new"
  ```

### 坑 4：上传后 nginx 403（目录权限 700）
- **现象**：首页 403 Forbidden
- **原因**：scp 从 Windows 拷出的目录权限是 `drwx------`（700，仅 root），nginx worker 以 nginx 用户运行读不了
- **解法**：`chmod -R 755 /opt/dist && chmod 644 /opt/dist/index.html`

### 坑 5：前端更新后线上"没变化"——浏览器缓存
- **现象**：重新构建部署后，页面行为还是旧的
- **原因**：浏览器缓存了旧 index.html（无强缓存头时仍可能命中启发式缓存）
- **解法**：① 无痕窗口或 Ctrl+Shift+R 验证；② 服务端确认法：`grep` 新文案是否在新 bundle、对比 nginx 返回的 index.html 与磁盘 md5；③ 版本文件是 hash 命名，确认加载到新 hash 即可。

### 坑 6：切换部署的原子操作与回滚
- **经验**：生产切换必须"备份 → 新目录 → 原子 mv 替换 → 验证 → 保留回滚路径"：
  ```bash
  rm -rf /opt/dist.prev && mv /opt/dist /opt/dist.prev && mv /opt/dist.new /opt/dist
  # 回滚: rm -rf /opt/dist && mv /opt/dist.prev /opt/dist
  ```
- 后端切换同理：旧 jar 先备份到 `/opt/backup/`（含启动参数记录），容器起不来时一条命令切回。

---

## 0. 服务器前置检查（两种方式都需要）

```bash
java -version          # 需 JDK 17+
mysql -uroot -p        # 数据库 career_plan 已初始化（sql/sql.sql）
redis-cli ping         # 返回 PONG
free -h                # 确认剩余内存（容器化建议 ≥ 700MB 空闲）
```

数据库已是最新表结构、无需迁移（本轮改动未涉及表结构）。

---

## A. 传统 jar 部署（当前线上方式）

### A1. 本地构建
```bash
# Windows（项目根目录）
mvnw.cmd clean package -DskipTests
# 产物: target/OdysseyGen-0.0.1-SNAPSHOT.jar
```

### A2. 上传 + 替换
```bash
# 本地上传（Windows PowerShell）
scp target/OdysseyGen-0.0.1-SNAPSHOT.jar root@<服务器IP>:/opt/odysseygen/app.jar

# 服务器上（假设旧进程用 nohup 启动）
ssh root@<服务器IP>
cd /opt/odysseygen
# 1) 备份旧包（回滚用）
cp app.jar app.jar.bak.$(date +%Y%m%d%H%M)
# 2) 停旧进程（按实际进程名/端口调整）
kill $(pgrep -f app.jar) || true
sleep 2
# 3) 启新进程（内存参数适配 2C2G）
nohup java -Xms256m -Xmx512m -XX:+UseG1GC -jar app.jar \
    --spring.datasource.username=root \
    --spring.datasource.password=<DB密码> \
    --jwt.secret=<JWT密钥> \
    --deepseek.api.key=<DeepSeekKey> \
    > app.log 2>&1 &
# 4) 验证
tail -f app.log            # 看到 "Started OdysseyGenApplication"
curl -s http://127.0.0.1:8080/api/user/verify -H "Authorization: Bearer x" # 401 即存活
```

### A3. 回滚
```bash
kill $(pgrep -f app.jar); mv app.jar.bak.最新 app.jar; 重新启动（同 A2-3）
```

---

## B. Docker 容器化部署

> 设计要点：**只容器化应用**，MySQL/Redis 继续跑宿主机；容器用 `network_mode: host`
> 直连 `localhost:3306/6379`——2C2G 下不起 DB 容器，避免内存挤爆 + 数据迁移风险。

### B1. 服务器安装 Docker
```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

### B2. 上传代码 + 配置 .env
```bash
# 本地：把项目根目录（Dockerfile/docker-compose.yml/src/pom.xml）上传到服务器 /opt/odysseygen-docker
cd /opt/odysseygen-docker
cp .env.example .env    # 或手动创建，见下方模板
vim .env
```

`.env` 模板：
```bash
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
JWT_SECRET=至少32位随机字符串（与现网一致，否则旧 token 全部失效！）
DEEPSEEK_API_KEY=你的DeepSeekKey
```

### B3. 构建 + 启动
```bash
docker compose up -d --build
docker compose logs -f app      # 看启动日志，出现 "Started OdysseyGenApplication" 即成功
docker compose ps               # 状态 Up
```

### B4. 常用运维
```bash
docker compose logs -f --tail 200 app   # 看日志
docker compose restart app              # 重启
docker compose down                     # 停止（容器删除，数据都在宿主机 DB，无丢失风险）
docker compose up -d --build            # 重新构建部署（新版本）
```

### B5. 回滚
```bash
# 方式1：git 切回旧版本重新 build
# 方式2：Docker 镜像打 tag 保留版本
docker tag odysseygen:latest odysseygen:20260902
docker compose up -d --build    # 新版有问题时:
docker run -d --name odysseygen-rollback --network host odysseygen:20260902
```

---

## 部署后验证清单（两种方式通用）

1. **接口存活**：`curl http://127.0.0.1:8080/api/user/login`（POST 空体应返回参数校验错误而非 500/连接失败）
2. **登录**：用现网账号登录拿 token
3. **核心链路**：调一次 `/api/plan/generate`（命中缓存应秒回）或 `/api/plan/generate-async`
4. **降级验证（可选但推荐）**：`systemctl stop redis`（或 `docker stop redis容器`）→ 调 generate 应降级直连返回（看日志 `[降级模式]`）→ 恢复 Redis
5. **日志**：确认无 `ERROR`、无 OOM（`dmesg | grep -i oom`）

---

## 上线前安全注意事项（代码审查结论，重要）

本轮已修复：
- ✅ 移除 SQL 种子中的演示管理员账号（`testuser/123456`，公开仓库可被直接登录获取 ADMIN）
- ✅ JWT 密钥启动期强度校验（<32 字节直接启动失败，杜绝"默认占位符静默 401"）
- ✅ `ProfileRequest.graduationYear` 加 `@NotNull` + DeepSeekUtil 空值防御与"已毕业"判定
- ✅ Redis 降级 catch 覆盖 `QueryTimeoutException`；幂等拦截器 Redis 故障 fail-open

部署时必须注意（未修但影响上线）：
1. **⚠️ 服务器生产密钥**：务必通过环境变量注入，**不要**用仓库里的 `application-local.yml`（其内容仅供本地开发）。数据库若曾用 `init.sql` 建库，检查是否残留 `testuser` 账号并删除。
2. **⚠️ 若部署后所有 AI 生成任务 FAILED（400）**：先冒烟验证模型名 `deepseek.api.model`（默认 `deepseek-v4-pro`）在 DeepSeek API 是否可用，不可用则改为官方登记的模型名（如 `deepseek-chat`）后重启。
3. `/api/plan/generate` 同步接口无独立限流/熔断（前端实际走 async 接口，低风险，知悉即可）。
4. 日志会打印 SQL 与 debug 信息（`application.yml`），生产流量大时可调低日志级别。
5. JWT 密钥轮换会使线上所有已签发 token 失效（含用户登录态），非必要不换。

---

## 前端（OdysseyGen-web）说明

- 前端为 Vue3 + Vite，独立构建部署（本指南不涉及，仍按现网 Nginx 方式）；
- 若前端请求代理 `/api` 指向 `http://127.0.0.1:8080`，后端两种部署方式均无需改前端配置（端口不变 8080）。

---

## 2C2G 内存预算参考

| 组件 | 预算 |
|---|---|
| 宿主机系统 | ~200MB |
| MySQL | ~400-500MB（可调 buffer pool） |
| Redis | ~50-100MB |
| JVM（应用） | 512MB 堆 + 元空间/线程 ≈ 700MB |
| Docker 层开销 | 仅容器化时 +50MB 左右 |

> 若容器化后内存吃紧：`mem_limit` 已设 768m；JVM `-Xmx512m` 在 Dockerfile 中已固定；
> 再紧可调 MySQL `innodb_buffer_pool_size`（需改 my.cnf 重启 MySQL，谨慎操作）。
