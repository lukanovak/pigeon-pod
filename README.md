<div align="center">
  <img src="frontend/src/assets/pigeon.png" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Turn your favorite YouTube channels into podcast channels in the simplest and most elegant way.</h2>
  <p>If self-hosting isn't your thing, take a look at our upcoming online services:
    <a target="_blank" href="https://pigeonpod.asimov.top/">PigeonPod</a>
  </p>
</div>

[![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red?style=flat-square)](README-ZH.md)

## Screenshots

- Channel list
![index-dark&light](documents/screenshots/index-dark&light.png)

- Channel detail
![detail-dark&light](documents/screenshots/detail-dark&light.png)

## Core Features

- **🎯 Smart Channel Subscription**: Quickly add channels through YouTube channel URLs
- **🤖 Auto Sync Updates**: Automatically check and sync latest channel content with incremental updates
- **📻 RSS Podcast Subscription**: Generate standard RSS subscription links, compatible with any podcast client
- **🔍 Content Filtering**: Support keyword filtering (include/exclude) and episode duration filtering
- **📊 Episode Management**: View, delete, and retry failed episode downloads
- **✨ Ad-free Listening**: Automatically remove intro and mid-roll ads from episodes
- **🌐 Multi-language Support**: Complete support for Chinese and English interfaces
- **📱 Responsive UI**: Excellent experience on any device, anytime, anywhere

## Deployment

### Using Docker Compose (Recommended)

1. Use the docker-compose configuration file, modify environment variables according to your needs
```yml
version: '3.9'
services:
  pigeon-pod:
    # Find the latest version at https://github.com/aizhimou/pigeon-pod/pkgs/container/pigeon-pod
    image: 'ghcr.io/aizhimou/pigeon-pod:release-${version}' # replace ${version} with the latest version number, e.g. 1.0.0.
    restart: unless-stopped
    container_name: pigeon-pod
    ports:
      - '8834:8080'
    environment:
      - 'PIGEON_BASE_URL=https://pigeonpod.asimov.top' # set to your domain
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # set to your audio file path
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # set to your database path
    volumes:
      - data:/data

volumes:
  data:
```

2. Start the service
```bash
docker-compose up -d
```

3. Access the application
Open your browser and visit `http://localhost:8834` with **default username and password: `root/Root@123.`**

## Documentation

- [How to get YouTube API Key](https://github.com/mxpv/podsync/blob/main/docs/how_to_get_youtube_api_key.md)
- [Youtube cookie setup](documents/youtube-cookie-setup-en.md)


## Tech Stack

### Backend
- **Java 17** - Core language
- **Spring Boot 3.5** - Application framework
- **SQLite** - Lightweight database
- **Sa-Token** - Authentication framework
- **YouTube Data API v3** - YouTube data retrieval
- **yt-dlp** - Video download tool
- **Rome** - RSS generation library

### Frontend
- **React 19** - UI framework
- **Vite 7** - Build tool
- **Mantine 8** - UI component library
- **React Router 7** - Route management
- **i18next** - Internationalization support
- **Axios** - HTTP client

## Development Guide

### Environment Requirements
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Local Development

1. Clone the project
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Configure database
```bash
# Create data directory
mkdir -p data/audio

# Database file will be created automatically on first startup
```

3. Configure YouTube API
   - Create a project in [Google Cloud Console](https://console.cloud.google.com/)
   - Enable YouTube Data API v3
   - Create an API key
   - Configure the API key in user settings

4. Start backend
```bash
cd backend
mvn spring-boot:run
```

5. Start frontend (new terminal)
```bash
cd frontend
npm install
npm run dev
```

6. Access the application
- Frontend dev server: `http://localhost:5173`
- Backend API: `http://localhost:8080`

### Project Structure
```
pigeon-pod/
├── backend/                 # Spring Boot backend
│   ├── src/main/java/      # Java source code
│   │   └── top/asimov/pigeon/
│   │       ├── controller/ # REST API controllers
│   │       ├── service/    # Business logic services
│   │       ├── mapper/     # Data access layer
│   │       ├── model/      # Data models
│   │       ├── scheduler/  # Scheduled tasks
│   │       └── worker/     # Async workers
│   └── src/main/resources/ # Configuration files
├── frontend/               # React frontend
│   ├── src/
│   │   ├── components/     # Reusable components
│   │   ├── pages/         # Page components
│   │   ├── context/       # React Context
│   │   └── helpers/       # Utility functions
│   └── public/            # Static assets
├── data/                  # Data storage directory
│   ├── audio/            # Audio files
│   └── pigeon-pod.db     # SQLite database
├── docker-compose.yml    # Docker orchestration config
└── Dockerfile           # Docker image build
```

### Development Notes
1. Ensure yt-dlp is installed and available in command line
2. Configure correct YouTube API key
3. Ensure audio storage directory has sufficient disk space
4. Regularly clean up old audio files to save space
