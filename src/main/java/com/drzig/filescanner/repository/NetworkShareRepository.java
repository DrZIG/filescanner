package com.drzig.filescanner.repository;

import com.drzig.filescanner.model.NetworkShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkShareRepository extends JpaRepository<NetworkShare, Long> {
    List<NetworkShare> findByEnabledTrue();
    Optional<NetworkShare> findByPath(String path);
}
