package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.model.Bookmark;
import com.reader.Novel.Reader.repository.UserRepository;
import com.reader.Novel.Reader.repository.BookmarkRepository;
import com.reader.Novel.Reader.repository.PurchaseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.RatingRepository ratingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /*List<User> users = new ArrayList<>(Arrays.asList(
            new User(100L,"ChepuNiga","Chepu@gmail.com","1234A","human"),
            new User(101L,"Aritra","Ari@gmail.com","123A4",null)));*/

    public List<User> getUsers(){
        return userRepository.findAll();
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(new User());
    }

    @Transactional
    public void addUser(User usr){
        resetAutoIncrement();
        userRepository.save(usr);
        resetAutoIncrement();
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public void updateUser(User usr) {
        userRepository.save(usr);
        resetAutoIncrement();
    }

    @Transactional
    public void deleteUser(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        bookmarkRepository.deleteAll(bookmarks);
        ratingRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
        resetAutoIncrement();
    }

    @Transactional
    public void updateUserId(Long oldId, Long newId) {
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        try {
            entityManager.createNativeQuery("UPDATE bookmarks SET user_id = :newId WHERE user_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE purchases SET user_id = :newId WHERE user_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE ratings SET user_id = :newId WHERE user_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE novels SET creator_id = :newId WHERE creator_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE reader_internal SET id = :newId WHERE id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();
        } finally {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
            resetAutoIncrement();
        }
    }

    private void resetAutoIncrement() {
        List<User> all = userRepository.findAll();
        long maxId = 0;
        for (User u : all) {
            if (u.getId() != null && u.getId() > maxId) {
                maxId = u.getId();
            }
        }
        long nextId = maxId + 1;
        entityManager.createNativeQuery("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH " + nextId).executeUpdate();
    }
}

