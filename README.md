# DrZIG FileScanner

A Java 17 / Spring Boot 3 web application to scan all local and network drives and store the file/folder hierarchy in a database, with a browser-based explorer UI.

---

## Requirements

- **Java 17+** (Java 21 recommended)
- **Maven 3.8+**
- Windows PC (primary target; also works on Linux/macOS for local drives)

---

## Quick Start

### 1. Build

```
mvn clean package -DskipTests
```

### 2. Run (H2 embedded database — default)

```
java -jar target/file-scanner-1.0.0.jar
```

### 3. Open browser

```
http://localhost:8080
```

---

## Database Profiles

Two Spring profiles are provided. Switch with `--spring.profiles.active=`:

### H2 (default — for local use / debugging)

```
java -jar file-scanner-1.0.0.jar --spring.profiles.active=h2
```

- Embedded file-based database, no installation needed
- Database file: `filescanner-db.mv.db` in the working directory
- H2 web console at: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:file:./filescanner-db`
  - Username: `sa`, Password: (blank)

### PostgreSQL (for production / persistent storage)

```
java -jar file-scanner-1.0.0.jar --spring.profiles.active=postgres
```

Configure connection in `application-postgres.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/filescanner
spring.datasource.username=filescanner
spring.datasource.password=changeme
```

Create the database first:

```sql
CREATE DATABASE filescanner;
CREATE USER filescanner WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE filescanner TO filescanner;
```

---

## Features

### 🔍 Scanning

- Detects all local drives (C:, D:, etc.) automatically
- Scans configured network shares (UNC paths) with optional credentials
- Pre-scan: verifies all existing DB records still exist on disk; removes stale entries
- Root drives that become inaccessible are flagged — you confirm removal on the Drives page
- Scanning can be **started, paused, resumed, and stopped** from the web UI at any time
- Live status shown in the top status bar and on the home page (configurable refresh interval)

### ⏸ Pause / Resume

- The **Pause** button appears in the status bar and home page while scanning
- Pausing suspends processing at the next directory boundary (within ~1 second)
- **Resume** continues from exactly where it left off
- Stop terminates the scan immediately (at next directory boundary)

### 🌐 Network Shares (new dedicated page)

- Add UNC paths via the **Net Shares** page (`/shares`)
- **Probe** button tests accessibility and auto-detects whether credentials are needed
- If credentials are required, username / password / domain fields appear automatically
- On Windows, uses `net use` to connect authenticated shares before scanning
- Each share can be enabled/disabled individually
- Credentials stored in the database (plain text — home/lab use; encrypt at rest for production)

### 🚫 Do Not Process

- Any folder can be marked **Do Not Process** via checkbox in the UI or during a scan
- When toggled ON: all children are immediately purged from the database; the folder itself stays
- During scanning: any new subfolder whose ancestor is marked is automatically skipped

### 📁 Explorer

- Hierarchical navigation like Windows Explorer with breadcrumb trail
- File sizes shown in human-readable format (B, KB, MB, GB, TB)
- Emoji icons per file extension / folder name (fully configurable in Settings → Icons)
- Missing files/folders shown with strikethrough

### 📧 Email Report

- Sends a **zipped text file** attachment
- Report format: one path per line, size in bytes for files:
  ```
  C:\Temp\archive.zip 14362488
  //drzig-nas/Films/Common folder/Film.avi 1436488
  ```
- Can be sent after scan, or at any time with the "Send Report" button
- Configure SMTP in **Settings → Email/SMTP**

### 🎨 Icon Mappings

- 50+ built-in file type icons (video, audio, images, documents, code, archives, etc.)
- Add custom mappings in **Settings → Icons** (extension or folder name matching)

---

## Email Setup (Gmail)

1. Enable 2-Factor Authentication on your Google account
2. Go to: https://myaccount.google.com/apppasswords
3. Generate an App Password for "Mail"
4. In FileScanner **Settings → Email**:
   - Host: `smtp.gmail.com`, Port: `587`, STARTTLS: `Yes`
   - Username: your Gmail address
   - Password: the 16-character App Password

---

## Changing the Port

```
java -jar file-scanner-1.0.0.jar --server.port=9090
```

---

## Running as a Windows Service (WinSW)

Download [WinSW](https://github.com/winsw/winsw) and create `filescanner-service.xml`:

```xml
<service>
  <id>filescanner</id>
  <name>DrZIG FileScanner</name>
  <description>File system scanner and browser</description>
  <executable>java</executable>
  <arguments>-jar "C:\filescanner\file-scanner-1.0.0.jar" --spring.profiles.active=postgres</arguments>
  <workingdirectory>C:\filescanner</workingdirectory>
  <logmode>rotate</logmode>
</service>
```

Install: `winsw install filescanner-service.xml`

---

## Project Structure

```
src/main/java/com/drzig/filescanner/
├── FileScannerApplication.java
├── config/AsyncConfig.java
├── controller/MainController.java
├── dto/
│   ├── FileEntryDto.java
│   └── ScanStatus.java          ← IDLE / RUNNING / PAUSED / COMPLETED / ERROR
├── model/
│   ├── AppSettings.java
│   ├── FileEntry.java
│   ├── IconMapping.java
│   └── NetworkShare.java        ← UNC path + optional credentials
├── repository/  (4 Spring Data JPA repos)
└── service/
    ├── ScanService.java         ← async scan with pause/resume
    ├── FileService.java
    ├── NetworkShareService.java  ← probe + net use connect
    ├── EmailService.java
    ├── IconService.java
    └── SettingsService.java

src/main/resources/
├── application.properties           ← sets active profile to 'h2'
├── application-h2.properties        ← H2 config
├── application-postgres.properties  ← PostgreSQL config
└── templates/
    ├── layout.html
    ├── index.html        ← home + scan control
    ├── browse.html       ← folder explorer
    ├── shares.html       ← network share management
    ├── settings.html     ← general / email / icons
    └── manage-roots.html ← drive DB management
```
