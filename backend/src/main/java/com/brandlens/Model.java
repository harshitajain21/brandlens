package com.brandlens;
import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="brand_rules") class BrandRule {
 @Id @GeneratedValue(strategy=GenerationType.UUID) String id;
 @Column(unique=true,nullable=false) String ruleCode; String title,category,description,severity,sourceReference;
 @Column(length=3000) String sourceQuote; String prohibitedTerm,preferredTerm; boolean active=true;
 protected BrandRule(){} BrandRule(String code,String title,String cat,String desc,String sev,String ref,String quote,String bad,String good){ruleCode=code;this.title=title;category=cat;description=desc;severity=sev;sourceReference=ref;sourceQuote=quote;prohibitedTerm=bad;preferredTerm=good;}
}
@Entity @Table(name="approved_facts") class ApprovedFact {
 @Id @GeneratedValue(strategy=GenerationType.UUID) String id;
 @Column(unique=true,nullable=false) String factKey; @Column(length=1000) String approvedValue; String description,sourceReference;
 @Column(length=3000) String sourceQuote; LocalDate validFrom,validUntil; boolean active=true;
 protected ApprovedFact(){} ApprovedFact(String key,String value,String desc,String ref,String quote,LocalDate from,LocalDate until){factKey=key;approvedValue=value;description=desc;sourceReference=ref;sourceQuote=quote;validFrom=from;validUntil=until;}
}
@Entity @Table(name="reviews") class Review {
 @Id @GeneratedValue(strategy=GenerationType.UUID) String id; @Column(length=12000,nullable=false) String submittedContent;
 String channel,currentStatus="IN_REVIEW"; Instant createdAt=Instant.now(),updatedAt=Instant.now();
 protected Review(){} Review(String content,String channel){submittedContent=content;this.channel=channel;}
}
@Entity @Table(name="findings") class Finding {
 @Id @GeneratedValue(strategy=GenerationType.UUID) String id; @Column(nullable=false) String reviewId,category,classification,severity,matchedText,explanation;
 int startOffset,endOffset; String suggestedText,sourceRuleId,sourceFactId; @Column(length=3000) String sourceQuote; String sourceReference,engineType,decisionStatus="OPEN",decisionReason;
 protected Finding(){} Finding(String review,String category,String classification,String severity,int start,int end,String matched,String explanation,String suggested,String rule,String fact,String quote,String ref,String engine){reviewId=review;this.category=category;this.classification=classification;this.severity=severity;startOffset=start;endOffset=end;matchedText=matched;this.explanation=explanation;suggestedText=suggested;sourceRuleId=rule;sourceFactId=fact;sourceQuote=quote;sourceReference=ref;engineType=engine;}
}
@Entity @Table(name="audit_events") class AuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) String id; String reviewId,findingId,action,reason,actor="Demo reviewer"; @Column(length=12000) String oldValue,newValue; Instant timestamp=Instant.now();
 protected AuditEvent(){} AuditEvent(String r,String f,String a,String oldV,String newV,String reason){reviewId=r;findingId=f;action=a;oldValue=oldV;newValue=newV;this.reason=reason;}
}
