package com.dbdesigner.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Project> findByShareToken(String shareToken);
}
