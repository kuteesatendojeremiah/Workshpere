package com.worksphere.team;

import com.worksphere.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "team_members")
public class TeamMember {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false)
    private TeamRole teamRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamMember() {
    }

    public TeamMember(Team team, User user, TeamRole teamRole) {
        this.team = team;
        this.user = user;
        this.teamRole = teamRole;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public User getUser() {
        return user;
    }

    public TeamRole getTeamRole() {
        return teamRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
