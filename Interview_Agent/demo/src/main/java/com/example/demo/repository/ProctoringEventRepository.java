package com.example.demo.repository;

import com.example.demo.entity.ProctoringEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProctoringEventRepository extends JpaRepository<ProctoringEvent, Long> {

    List<ProctoringEvent> findByInterviewIdOrderByCapturedAtAsc(String interviewId);
}
