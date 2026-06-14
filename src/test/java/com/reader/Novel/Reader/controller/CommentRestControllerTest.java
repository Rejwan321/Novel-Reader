package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Comment;
import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.CommentRepository;
import com.reader.Novel.Reader.repository.NovelRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CommentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User testUser;
    private Novel testNovel;
    private Chapter testChapter;
    private MockHttpSession session;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 100");

        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("testuser@example.com");
        testUser.setPassword("password123");
        testUser.setUser_type("READER");
        testUser = userRepository.save(testUser);

        testNovel = new Novel();
        testNovel.setTitle("Test Novel");
        testNovel.setAuthor("Test Author");
        testNovel.setDescription("A test novel description");
        testNovel.setType("NOVEL");
        testNovel.setGenre("Fantasy");
        testNovel.setRating(4.5);
        testNovel.setStatus("ONGOING");
        testNovel = novelRepository.save(testNovel);

        testChapter = new Chapter();
        testChapter.setNovel(testNovel);
        testChapter.setTitle("Chapter 1");
        testChapter.setChapterNumber(1.0);
        testChapter.setContent("Chapter content text");
        testChapter = chapterRepository.save(testChapter);

        session = new MockHttpSession();
        session.setAttribute("user", testUser);
    }

    @Test
    public void testAddCommentAndGetComments() throws Exception {
        // 1. Add top-level comment
        mockMvc.perform(post("/api/chapters/" + testChapter.getId() + "/comments")
                .session(session)
                .param("content", "This is a root comment."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("This is a root comment."))
                .andExpect(jsonPath("$.user.id").value(testUser.getId().intValue()));

        // 2. Fetch comments and verify
        mockMvc.perform(get("/api/chapters/" + testChapter.getId() + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("This is a root comment."))
                .andExpect(jsonPath("$[0].replies", hasSize(0)));
    }

    @Test
    public void testLikeAndDislikeComment() throws Exception {
        Comment comment = new Comment(testChapter.getId(), testUser, "Root comment for likes");
        comment = commentRepository.save(comment);

        // 1. Like comment
        mockMvc.perform(post("/api/comments/" + comment.getId() + "/like")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.dislikes").value(0))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.disliked").value(false));

        // 2. Like again (toggle off)
        mockMvc.perform(post("/api/comments/" + comment.getId() + "/like")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(0))
                .andExpect(jsonPath("$.liked").value(false));

        // 3. Dislike comment
        mockMvc.perform(post("/api/comments/" + comment.getId() + "/dislike")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(0))
                .andExpect(jsonPath("$.dislikes").value(1))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.disliked").value(true));

        // 4. Like comment (toggles dislike off, like on)
        mockMvc.perform(post("/api/comments/" + comment.getId() + "/like")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.dislikes").value(0))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.disliked").value(false));
    }

    @Test
    public void testReplyToComment() throws Exception {
        Comment parentComment = new Comment(testChapter.getId(), testUser, "Parent comment");
        parentComment = commentRepository.save(parentComment);

        // 1. Post a reply
        mockMvc.perform(post("/api/comments/" + parentComment.getId() + "/reply")
                .session(session)
                .param("content", "This is a nested reply."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("This is a nested reply."));

        // 2. Verify root comment lists replies nested
        mockMvc.perform(get("/api/chapters/" + testChapter.getId() + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(parentComment.getId().intValue()))
                .andExpect(jsonPath("$[0].replies", hasSize(1)))
                .andExpect(jsonPath("$[0].replies[0].content").value("This is a nested reply."));
    }

    @Test
    public void testDeleteParentCommentCascadesToReplies() throws Exception {
        Comment parentComment = new Comment(testChapter.getId(), testUser, "Parent comment");
        parentComment = commentRepository.save(parentComment);

        Comment replyComment = new Comment(testChapter.getId(), testUser, "Reply comment");
        replyComment.setParent(parentComment);
        parentComment.getReplies().add(replyComment);
        parentComment = commentRepository.save(parentComment);

        // Verify reply is stored
        List<Comment> allCommentsBefore = commentRepository.findAll();
        Comment savedReply = parentComment.getReplies().get(0);
        final Long replyId = savedReply.getId();
        assertTrue(allCommentsBefore.stream().anyMatch(c -> c.getId().equals(replyId)));

        // Delete parent comment via API
        mockMvc.perform(delete("/api/comments/" + parentComment.getId())
                .session(session))
                .andExpect(status().isOk());

        // Flush and clear L1 cache to ensure database state is synced and queried directly
        entityManager.flush();
        entityManager.clear();

        // Verify parent is deleted
        assertFalse(commentRepository.findById(parentComment.getId()).isPresent());

        // Verify reply is also deleted (cascading / orphan removal)
        assertFalse(commentRepository.findById(replyId).isPresent());
    }
}
