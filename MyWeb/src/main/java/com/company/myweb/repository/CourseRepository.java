package com.company.myweb.repository;

import com.company.myweb.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Course 的 Spring Data JPA repository。
 * 兩個方法都是 derived query 內建的靜態排序（OrderBy）。
 * 亦可透過 Spring Data REST 端點測試：/spring-data-api/courses/search
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {

    List<Course> findByOrderByNameDesc();  // 依 name 降冪

    List<Course> findByOrderByName();      // 依 name 升冪（預設）
}
