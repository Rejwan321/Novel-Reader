# Yuki Tales (Novel & Comic Reader Platform)

Yuki Tales is a premium, highly responsive web application designed for reading web novels and vertical comics. It features a customizable reading layout (fonts, sizes, light/sepia/dark themes), bookmark management, user accounts, and content analytics.

---

## 🚀 Key Features

* **Responsive Reader Layout:** Seamless reading layout on mobile and desktop viewports, locking canvas zoom to guarantee layout stability.
* **Snow Flakes Purchase System:** Premium chapters can be unlocked using digital flakes (mock checkout system).
* **Multi-Role User Authentication:** Supports multiple role configurations:
  * **OWNER:** God-mode master permissions. Accesses a toggleable "Self-Destruct" button to secure all site content and database data (analytics, chapters, and stories) from lower roles.
  * **ADMIN:** Complete dashboard management.
  * **EDITOR / PROOFREADER:** Create and modify chapters/novels.
  * **READER:** Normal browsing, reading, rating, bookmarking, and purchasing.
* **Snappy Snaptarget Carousel Filters:** Smooth, touch-supported client-side filters for categories (Novels, Comics, Manhwa, Manga) and genres.

---

## 🛠️ Tech Stack

* **Backend:** Spring Boot (Java 21), Spring Data JPA
* **Database:** H2 File Database (Local file storage under `./data/bookstore`)
* **Template Engine:** Thymeleaf (HTML5 / Bootstrap 5 / Outfit & Poppins Fonts)
* **Frontend Logic & Styling:** Vanilla Javascript, jQuery, CSS3 Custom Theme

---

## 📋 Getting Started (Local Development)

### Prerequisites
* **Java:** JDK 21 installed.
* **Build Tool:** Maven (included wrapper `mvnw.cmd` can be used).

### Running the Application

1. **Clone the Repository** (or download the source code).
2. **Compile the Static Assets:**
   ```powershell
   .\mvnw.cmd compile
   ```
3. **Run the Spring Boot Server:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. **Access the Application:**
   Open [http://localhost:8080/](http://localhost:8080/) in your browser.

---
