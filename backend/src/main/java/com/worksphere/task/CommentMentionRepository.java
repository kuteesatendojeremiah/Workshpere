package com.worksphere.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentMentionRepository extends JpaRepository<CommentMention, UUID> {

    List<CommentMention> findAllByCommentId(UUID commentId);
}
