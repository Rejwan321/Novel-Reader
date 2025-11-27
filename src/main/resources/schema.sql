CREATE TABLE Books (
book_id INTEGER AUTO_INCREMENT PRIMARY KEY,
title VARCHAR(255) NOT NULL,
genre VARCHAR(100), description TEXT,
cover_image_url VARCHAR(255),
author_id BIGINT,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
FOREIGN KEY (author_id) REFERENCES reader(id)
);
--insert into book(title,pages,author) values('Spring Boot : Up and Running', 300, 'Dan Vega');
--ALTER TABLE books ADD FOREIGN KEY (author_id) REFERENCES reader(id);