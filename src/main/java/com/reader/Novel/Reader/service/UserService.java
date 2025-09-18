package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /*List<User> users = new ArrayList<>(Arrays.asList(
            new User(100L,"ChepuNiga","Chepu@gmail.com","1234A","human"),
            new User(101L,"Aritra","Ari@gmail.com","123A4",null)));*/

    public List<User> getUsers(){
        return userRepository.findAll();
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(new User());
    }

    public void addUser(User usr){
        userRepository.save(usr);
    }

    public void updateUser(User usr) {
        userRepository.save(usr);
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
