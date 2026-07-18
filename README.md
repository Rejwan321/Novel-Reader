# 🌸 Yuki Tales (Web Novel & Comic Platform)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Database](https://img.shields.io/badge/Database-H2-orange.svg)](https://www.h2database.com/)

Yuki Tales is a premium, high-performance web platform designed for hosting, reading, and managing web novels and vertical comics. Built with an optimized Spring Boot backend and a sleek, responsive Thymeleaf & Bootstrap frontend, it delivers an immersive reading experience with customizable layouts, real-time interactions, and secure premium content unlocking.

---

## 🚀 Core Features

### 📖 Immersive Reading Layout
* **Fluid Viewports:** Fully responsive reader UI adapting flawlessly to mobile, tablet, and desktop screens.
* **Canvas Lock:** Zoom-locking controls to guarantee layout stability.
* **Reader Settings:** Toggleable fonts, sizes, line heights, and custom themes (Light, Sepia, Dark/OLED).

### 🛡️ Enterprise-Grade Security Hardening
* **Secure Authentication:** Implemented Spring Security filter chains with session-fixation protection.
* **BCrypt Migration:** Automatic, silent user password upgrade from legacy AES encryption to modern `BCryptPasswordEncoder` on login.
* **XSS Mitigation:** Integrated `jsoup` HTML sanitization on all chapter publication paths to strip malicious scripts and event handlers.
* **Malicious File Protection:** Strict magic-bytes file signature checking for PNG, JPEG, GIF, and WEBP image uploads to block disguised web shells.

### 💬 Real-Time Comments & Soft-Deletion
* **Threaded Comment Feed:** Real-time commenting and nested replies powered by Server-Sent Events (SSE).
* **Smart Soft-Deletion:**
  * Deleted comments display a placeholder (`This comment has been deleted.`) to preserve reply trees for standard users.
  * Parent comments with no active replies are completely filtered out.
  * Administrators can view soft-deleted comments clearly highlighted with `[Deleted]` prefixes and original text.
  * **Permanent Deletion:** Administrators can click delete on a soft-deleted comment to remove it permanently from the database.

### 💳 Monetization & Coupons
* **Digital Snow Flakes:** Internal token economy for purchasing and unlocking premium chapters.
* **Payment Gateways:** Standardized integrations for **Razorpay**, with a local **Mock Checkout** option restricted to admin accounts.
* **Coupon System:** System-wide discount coupons with support for percentage-off rates and strict restrictions by user email/username.
* **Sign-up Alerts:** Configurable system alerts to notify admins via email upon new user registrations (configurable in credentials settings).

---

## ⚡ Performance Optimizations

* **Tomcat Thread Tuning:** Configured Tomcat connector pool with `max-threads=200`, `max-connections=2000`, and `accept-count=500` to handle high concurrent requests.
* **HikariCP Tuning:** Configured connection leak-detection thresholds (`2000ms`) and idle timeouts (`10000ms`) to recycle database resources.
* **Payload Compression:** GZIP compression enabled on JSON and HTML payloads to minimize network transit time.
* **Aggressive Caching:** Long-term HTTP caching headers configured for static assets and user uploads (`/uploads/**` cached for 1 year; `/css/**`, `/js/**`, and `/scripts/**` cached for 24 hours).

---

## 🛠️ Tech Stack

* **Backend Framework:** Spring Boot 3.4.5, Spring Security, Spring Data JPA
* **Database Engine:** H2 Database (File-based storage locally under `./data/bookstore`)
* **Templates & View Layer:** Thymeleaf HTML5 templates, Bootstrap 5, FontAwesome Icons
* **Client Logic:** Vanilla Javascript, jQuery, Server-Sent Events (SSE)
* **Build Automation:** Maven 3.9.x (wrapped)

---

## 📋 Getting Started

### Prerequisites
* **Java SDK:** OpenJDK 21 or newer installed.
* **Build System:** Maven wrapper (`mvnw`) included in the project root.

### Local Development Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/PartnerKiller/yuki-tales.git
   cd yuki-tales
   ```

2. **Configure environment properties (Optional):**
   Create or edit the environment variable parameters or override `src/main/resources/application.properties`.

3. **Build the application:**
   ```bash
   # On Linux/macOS
   ./mvnw clean compile
   
   # On Windows (PowerShell)
   .\mvnw.cmd clean compile
   ```

4. **Run the Spring Boot application:**
   ```bash
   # On Linux/macOS
   ./mvnw spring-boot:run
   
   # On Windows (PowerShell)
   .\mvnw.cmd spring-boot:run
   ```

5. **Access the application:**
   Navigate to [http://localhost:8080](http://localhost:8080).

---


