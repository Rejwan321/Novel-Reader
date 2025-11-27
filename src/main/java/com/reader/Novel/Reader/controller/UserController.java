package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.service.UserService;
import com.reader.Novel.Reader.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    @Autowired
    UserService service;

    @GetMapping("/users")
    public List<User> getUsers() {
        return service.getUsers();
    }

    @GetMapping("/users/{userId}")
    public User getUserById(@PathVariable Long userId){
        return service.getUserById(userId);
    }

    @PostMapping("/users")
    public void addUser(@RequestBody User usr){
        System.out.println(usr);
        service.addUser(usr);
    }

    @PutMapping("/users")
    public void updateUser(@RequestBody User usr){
        System.out.println("Updated");
        service.updateUser(usr);
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable Long userId){
        service.deleteUser(userId);
    }
}
