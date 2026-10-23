package com.brandlens;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface RuleRepository extends JpaRepository<BrandRule,String>{List<BrandRule> findByActiveTrue();}
interface FactRepository extends JpaRepository<ApprovedFact,String>{List<ApprovedFact> findByActiveTrue();}
interface ReviewRepository extends JpaRepository<Review,String>{List<Review> findAllByOrderByCreatedAtDesc();}
interface FindingRepository extends JpaRepository<Finding,String>{List<Finding> findByReviewId(String id); Optional<Finding> findByIdAndReviewId(String id,String reviewId);}
interface AuditRepository extends JpaRepository<AuditEvent,String>{List<AuditEvent> findByReviewIdOrderByTimestampAsc(String id);}
