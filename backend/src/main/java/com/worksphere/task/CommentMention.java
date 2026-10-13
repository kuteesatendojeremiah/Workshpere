package com.worksphere.task;

import com.worksphere.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Stored for Step 16 (notifications) to notify a mentioned user - not consumed yet. */
@Entity
@Table(name = "comment_mentions")
public class CommentMention {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private User mentionedUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentMention() {
    }

    public CommentMention(Comment comment, User mentionedUser) {
        this.comment = comment;
        this.mentionedUser = mentionedUser;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Comment getComment() {
        return comment;
    }

    public User getMentionedUser() {
        return mentionedUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
