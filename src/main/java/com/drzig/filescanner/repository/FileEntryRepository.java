package com.drzig.filescanner.repository;

import com.drzig.filescanner.model.FileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileEntryRepository extends JpaRepository<FileEntry, Long> {

    List<FileEntry> findByParentIdOrderByFileAscNameAsc(Long parentId);

    List<FileEntry> findByParentIdIsNullOrderByNameAsc();

    Optional<FileEntry> findByFullPath(String fullPath);

    @Query("SELECT f FROM FileEntry f WHERE f.rootPath = :rootPath AND f.parentId IS NULL")
    Optional<FileEntry> findRootEntry(@Param("rootPath") String rootPath);

    @Modifying
    @Transactional
    @Query("DELETE FROM FileEntry f WHERE (f.fullPath LIKE :prefix1 OR f.fullPath LIKE :prefix2) AND f.id != :keepId")
    void deleteChildrenByPathPrefixes(@Param("prefix1") String prefix1,
                                      @Param("prefix2") String prefix2,
                                      @Param("keepId") Long keepId);

    @Modifying
    @Transactional
    @Query("DELETE FROM FileEntry f WHERE f.rootPath = :rootPath")
    void deleteByRootPath(@Param("rootPath") String rootPath);

    @Query("SELECT COUNT(f) FROM FileEntry f WHERE f.rootPath = :rootPath")
    long countByRootPath(@Param("rootPath") String rootPath);

    @Query("SELECT DISTINCT f.rootPath FROM FileEntry f ORDER BY f.rootPath")
    List<String> findAllRootPaths();

    @Query("SELECT f FROM FileEntry f WHERE f.rootPath = :rootPath")
    List<FileEntry> findAllByRootPath(@Param("rootPath") String rootPath);
}
