package com.drzig.filescanner.repository;

import com.drzig.filescanner.model.IconMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IconMappingRepository extends JpaRepository<IconMapping, Long> {
    Optional<IconMapping> findByPattern(String pattern);
    List<IconMapping> findByPatternType(String patternType);
}
