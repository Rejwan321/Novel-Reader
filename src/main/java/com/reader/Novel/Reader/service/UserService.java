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

    @Autowired
    private com.reader.Novel.Reader.repository.CommentRepository commentRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.NotificationRepository notificationRepository;

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
        ensureUniqueUsername(usr);
        userRepository.save(usr);
        resetAutoIncrement();
    }

    private void ensureUniqueUsername(User usr) {
        if (usr.getUsername() != null && !usr.getUsername().trim().isEmpty()) {
            return;
        }
        String baseName = usr.getName();
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "user";
        }
        String baseUsername = baseName.trim().toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
        if (baseUsername.isEmpty()) {
            baseUsername = "user";
        }
        String targetUsername = baseUsername;
        int suffix = 0;
        while (userRepository.findByUsernameIgnoreCase(targetUsername).isPresent()) {
            targetUsername = baseUsername + suffix;
            suffix++;
        }
        usr.setUsername(targetUsername);
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
        // 1. Delete bookmarks
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        bookmarkRepository.deleteAll(bookmarks);

        // 2. Delete ratings
        ratingRepository.deleteByUserId(userId);

        // 3. Delete targeting notifications
        List<com.reader.Novel.Reader.model.Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        notificationRepository.deleteAll(notifications);

        // 4. Delete comments and replies posted by this user
        List<com.reader.Novel.Reader.model.Comment> userComments = commentRepository.findByUserId(userId);
        List<com.reader.Novel.Reader.model.Comment> replies = new ArrayList<>();
        List<com.reader.Novel.Reader.model.Comment> parents = new ArrayList<>();
        for (com.reader.Novel.Reader.model.Comment c : userComments) {
            if (c.getParent() != null) {
                replies.add(c);
            } else {
                parents.add(c);
            }
        }
        for (com.reader.Novel.Reader.model.Comment r : replies) {
            if (r.getParent() != null && r.getParent().getReplies() != null) {
                r.getParent().getReplies().remove(r);
            }
            commentRepository.delete(r);
        }
        commentRepository.deleteAll(parents);

        // 5. Delete purchases
        purchaseRepository.deleteByUserId(userId);

        // 6. Delete user record
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

