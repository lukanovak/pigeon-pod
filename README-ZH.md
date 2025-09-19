<div align="center">
  <img src="frontend/src/assets/pigeon.png" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>简单优雅的将你喜爱的 YouTube 频道转换为播客频道。</h2>
  <p>如果你不喜欢自托管，欢迎看看我们即将上线的在线服务：
    <a target="_blank" href="https://pigeonpod.asimov.top/">PigeonPod</a>
  </p>
</div>

[![English README](https://img.shields.io/badge/README-English-red?style=flat-square)](README.md)

## Screenshots

- 订阅列表
![index-dark&light](documents/screenshots/index-dark&light.png)

- 节目详情
![detail-dark&light](documents/screenshots/detail-dark&light.png)

## 核心功能

- **🎯 智能频道订阅**: 通过 YouTube 频道 URL 快速添加频道
- **🤖 自动同步更新**: 定时检查并同步频道最新内容，支持增量更新
- **📻 RSS 播客订阅**: 生成标准 RSS 订阅链接，支持任何播客客户端
- **🔍 内容过滤**: 支持关键词（包含/排除）和节目时长过滤
- **📊 节目管理**: 查看、删除、重试下载失败的节目
- **✨ 免广告收听**: 自动去除节目片头和中间的贴片广告
- **🍪 自定义 Cookies**: 支持通过上传 Cookies 订阅年龄限制内容和会员节目内容
- **🌐 多语言支持**: 完整支持中文和英文界面
- **📱 自适应UI**: 随时随地在任何设备上获得优秀体验

## 部署方式

### 使用 Docker Compose（推荐）

1. 使用 docker-compose 配置文件，注意根据自己的需求修改环境变量
```yml
version: '3.9'
services:
  pigeon-pod:
    # 在这里查看最新版本 https://github.com/aizhimou/pigeon-pod/pkgs/container/pigeon-pod
    image: 'ghcr.io/aizhimou/pigeon-pod:release-${version}' # 替换 ${version} 为最新版本号，例如 1.0.0
    container_name: pigeon-pod
    ports:
      - '8834:8080'
    environment:
      - 'PIGEON_BASE_URL=https://pigeonpod.asimov.top' # 替换为你的域名
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # 替换为你的音频文件路径
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # 替换为你的数据库路径
    volumes:
      - data:/data

volumes:
  data:
```

2. 启动服务
```bash
docker-compose up -d
```

3. 访问应用
打开浏览器访问 `http://{localhost}:8834`，**默认用户名密码：`root/Root@123.`**

## 文档

- [如何获取 YouTube Data API 密钥](documents/how-to-get-youtube-api-key-zh.md)
- [如何配置 YouTube Cookies](documents/youtube-cookie-setup-zh.md)
- [如何获取 YouTube 频道 ID](documents/how-to-get-youtube-channel-id-en.md)


## 技术栈

### 后端
- **Java 17** - 核心语言
- **Spring Boot 3.5** - 应用框架
- **SQLite** - 轻量级数据库
- **Sa-Token** - 权限认证框架
- **YouTube Data API v3** - YouTube 数据获取
- **yt-dlp** - 视频下载工具
- **Rome** - RSS 生成库

### 前端
- **React 19** - 用户界面框架
- **Vite 7** - 构建工具
- **Mantine 8** - UI 组件库
- **React Router 7** - 路由管理
- **i18next** - 国际化支持
- **Axios** - HTTP 客户端

## 开发指南

### 环境要求
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### 本地开发

1. 克隆项目
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. 配置数据库
```bash
# 创建数据目录
mkdir -p data/audio

# 数据库文件会在首次启动时自动创建
```

3. 配置 YouTube API
   - 在 [Google Cloud Console](https://console.cloud.google.com/) 创建项目
   - 启用 YouTube Data API v3
   - 创建 API 密钥
   - 在用户设置中配置 API 密钥

4. 启动后端
```bash
cd backend
mvn spring-boot:run
```

5. 启动前端（新终端）
```bash
cd frontend
npm install
npm run dev
```

6. 访问应用
- 前端开发服务器: `http://localhost:5173`
- 后端 API: `http://localhost:8080`

### 项目结构
```
pigeon-pod/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/      # Java 源代码
│   │   └── top/asimov/pigeon/
│   │       ├── controller/ # REST API 控制器
│   │       ├── service/    # 业务逻辑服务
│   │       ├── mapper/     # 数据访问层
│   │       ├── model/      # 数据模型
│   │       ├── scheduler/  # 定时任务
│   │       └── worker/     # 异步工作器
│   └── src/main/resources/ # 配置文件
├── frontend/               # React 前端
│   ├── src/
│   │   ├── components/     # 可复用组件
│   │   ├── pages/         # 页面组件
│   │   ├── context/       # React Context
│   │   └── helpers/       # 工具函数
│   └── public/            # 静态资源
├── data/                  # 数据存储目录
│   ├── audio/            # 音频文件
│   └── pigeon-pod.db     # SQLite 数据库
├── docker-compose.yml    # Docker 编排配置
└── Dockerfile           # Docker 镜像构建
```

### 开发注意事项
1. 确保 yt-dlp 已安装并可在命令行中使用
2. 配置正确的 YouTube API 密钥
3. 确保音频存储目录有足够的磁盘空间
4. 定期清理旧的音频文件以节省空间
