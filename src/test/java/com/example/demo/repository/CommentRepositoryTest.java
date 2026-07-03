package com.example.demo.repository;

import com.example.demo.entity.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    private Long video1;
    private Long video2;

    @BeforeEach
    void setUp() {
        video1 = 100L;
        video2 = 200L;
        save(video1, null, "top1");
        save(video1, null, "top2");
        Comment top3 = save(video1, null, "top3");
        save(video1, top3.getId(), "reply1-to-top3");
        save(video1, top3.getId(), "reply2-to-top3");
        save(video2, null, "top4-on-video2");
    }

    private Comment save(Long videoId, Long parentId, String text) {
        Comment c = new Comment();
        c.setVideoId(videoId);
        c.setParentId(parentId);
        c.setUserId(1L);
        c.setAuthor("tester");
        c.setText(text);
        return commentRepository.save(c);
    }

    @Test
    void countByVideoIdAndParentIdIsNull_countsOnlyTopLevel() {
        long count = commentRepository.countByVideoIdAndParentIdIsNull(video1);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void countAllByVideoId_includesReplies() {
        long count = commentRepository.countAllByVideoId(video1);
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void findByVideoIdAndParentIdIsNullOrderByIdDesc_returnsTopLevelDesc() {
        List<Comment> comments = commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(video1);
        assertThat(comments).hasSize(3);
        assertThat(comments).extracting(Comment::getText)
                .containsExactly("top3", "top2", "top1");
    }

    @Test
    void findByParentIdOrderByIdAsc_returnsReplies() {
        Comment top3 = commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(video1).get(0);
        List<Comment> replies = commentRepository.findByParentIdOrderByIdAsc(top3.getId());
        assertThat(replies).hasSize(2);
        assertThat(replies).extracting(Comment::getText)
                .containsExactly("reply1-to-top3", "reply2-to-top3");
    }

    @Test
    void countByVideoIdIn_groupsByVideo() {
        List<Object[]> results = commentRepository.countByVideoIdIn(List.of(video1, video2));
        assertThat(results).hasSize(2);
    }

    @Test
    void deleteByVideoId_removesAllComments() {
        commentRepository.deleteByVideoId(video1);
        commentRepository.flush();

        assertThat(commentRepository.countAllByVideoId(video1)).isZero();
        assertThat(commentRepository.countAllByVideoId(video2)).isEqualTo(1L);
    }
}
