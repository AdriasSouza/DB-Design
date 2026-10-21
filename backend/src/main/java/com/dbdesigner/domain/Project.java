package com.dbdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dbType;

    /** Schema do diagrama (tabelas, colunas e relações) serializado como JSON. */
    @Lob
    @Column(nullable = false)
    private String schemaJson;

    @Column(unique = true)
    private String shareToken;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(Long ownerId, String name, String dbType, String schemaJson) {
        this.ownerId = ownerId;
        this.name = name;
        this.dbType = dbType;
        this.schemaJson = schemaJson;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
