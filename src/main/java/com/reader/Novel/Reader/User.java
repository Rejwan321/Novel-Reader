package com.reader.Novel.Reader;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    private Long id;

    private String name;
    private String email;
    private String password;
    private String user_type;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUser_type() { return user_type; }
    public void setUser_type(String user_type) { this.user_type = user_type; }

    @Override
    public String toString() {
        return "User {id=" + id + ", name='" + name + "', email='" + email + "', password='" + password + "', user_type='" + user_type + "'}";
    }
}
