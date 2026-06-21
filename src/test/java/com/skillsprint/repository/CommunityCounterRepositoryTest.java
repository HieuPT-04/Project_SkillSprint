package com.skillsprint.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.skillsprint.entity.CommunityPost;
import com.skillsprint.entity.CommunityRoom;
import com.skillsprint.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CommunityCounterRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired CommunityPostRepository postRepository;
    @Autowired CommunityRoomRepository roomRepository;

    User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId("counter-user");
        user.setEmail("counter@example.com");
        user.setFullName("Counter User");
        userRepository.saveAndFlush(user);
    }

    @Test
    void adjustPostCountersAreAtomicAndNeverNegative() {
        CommunityPost post = new CommunityPost();
        post.setAuthor(user);
        post.setContent("Counter test");
        post = postRepository.saveAndFlush(post);

        postRepository.adjustLikeCount(post.getPostId(), 1);
        postRepository.adjustLikeCount(post.getPostId(), 1);
        postRepository.adjustCommentCount(post.getPostId(), 1);
        postRepository.adjustCommentCount(post.getPostId(), -5);

        CommunityPost updated = postRepository.findById(post.getPostId()).orElseThrow();
        assertEquals(2, updated.getLikeCount());
        assertEquals(0, updated.getCommentCount());
    }

    @Test
    void adjustRoomMemberCountIsAtomicAndNeverNegative() {
        CommunityRoom room = new CommunityRoom();
        room.setName("Counter Room");
        room.setOwner(user);
        room.setMemberCount(1);
        room = roomRepository.saveAndFlush(room);

        roomRepository.adjustMemberCount(room.getRoomId(), 1);
        roomRepository.adjustMemberCount(room.getRoomId(), -5);

        CommunityRoom updated = roomRepository.findById(room.getRoomId()).orElseThrow();
        assertEquals(0, updated.getMemberCount());
    }
}
