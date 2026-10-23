package com.brandlens;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrandLensControllerTest {
  @Test void unapprovedSurveyPercentageIsUnsupportedAndRequestsVerification() {
    RuleRepository rules=mock(RuleRepository.class); FactRepository facts=mock(FactRepository.class);
    ReviewRepository reviews=mock(ReviewRepository.class); FindingRepository findings=mock(FindingRepository.class); AuditRepository audit=mock(AuditRepository.class);
    BrandRule tone=new BrandRule("TONE-01","Ground claims","TONE","Use evidence.","LOW","Guide","Prefer specific, evidenced benefits.",null,null);tone.id="rule-tone";
    ApprovedFact emissions=new ApprovedFact("carbon_reduction","30%","Emissions","Report","30% lower lifecycle emissions.",LocalDate.now().minusDays(1),LocalDate.now().plusDays(20));emissions.id="fact-emissions";
    when(rules.findByActiveTrue()).thenReturn(List.of(tone));when(facts.findByActiveTrue()).thenReturn(List.of(emissions));
    String content="Meet the LumaLeaf bottle — a smarter choice for conscious users. It has 50% lower lifecycle emissions than our old range, and 92% of families say it changed their daily habits. We think it is the most beautiful bottle on the market.";
    Review review=new Review(content,"SOCIAL");review.id="review-stats";when(reviews.findById(review.id)).thenReturn(Optional.of(review));when(reviews.save(any())).thenAnswer(i->i.getArgument(0));
    when(findings.findByReviewId(review.id)).thenReturn(List.of());
    BrandLensController controller=new BrandLensController(rules,facts,reviews,findings,audit);

    controller.analyze(review.id);

    ArgumentCaptor<Finding> saved=ArgumentCaptor.forClass(Finding.class);verify(findings,times(2)).save(saved.capture());
    Finding finding=saved.getAllValues().stream().filter(f->f.classification.equals("UNSUPPORTED")).findFirst().orElseThrow();
    assertEquals("92% of families say it changed their daily habits",finding.matchedText);
    assertEquals("rule-tone",finding.sourceRuleId);
    assertTrue(finding.explanation.contains("Verify it"));
    assertTrue(saved.getAllValues().stream().anyMatch(f->f.classification.equals("CONTRADICTED")&&f.matchedText.equals("50% lower")));
  }

  @Test void deterministicReviewLinksProhibitedTermAndContradictoryFact() {
    RuleRepository rules=mock(RuleRepository.class); FactRepository facts=mock(FactRepository.class);
    ReviewRepository reviews=mock(ReviewRepository.class); FindingRepository findings=mock(FindingRepository.class); AuditRepository audit=mock(AuditRepository.class);
    BrandRule word=new BrandRule("VOC-01","Use customers","TERMINOLOGY","Prefer customers.","MEDIUM","Voice guide","Use customers, not users.","users","customers");word.id="rule-1";
    BrandRule tone=new BrandRule("TONE-01","Ground claims","TONE","Use evidence.","LOW","Guide","Use evidenced benefits.",null,null);tone.id="rule-2";
    ApprovedFact fact=new ApprovedFact("carbon_reduction","30%","Emissions","Report","30% lower lifecycle emissions.",LocalDate.now().minusDays(1),LocalDate.now().plusDays(20));fact.id="fact-1";
    when(rules.findByActiveTrue()).thenReturn(List.of(word,tone)); when(facts.findByActiveTrue()).thenReturn(List.of(fact));
    Review review=new Review("Our users see 50% lower emissions.","SOCIAL");review.id="review-1";when(reviews.findById("review-1")).thenReturn(Optional.of(review));when(reviews.save(any())).thenAnswer(i->i.getArgument(0));
    BrandLensController controller=new BrandLensController(rules,facts,reviews,findings,audit);
    controller.analyze("review-1");
    ArgumentCaptor<Finding> saved=ArgumentCaptor.forClass(Finding.class);verify(findings,times(2)).save(saved.capture());
    assertTrue(saved.getAllValues().stream().anyMatch(f->f.classification.equals("CONTRADICTED")&&"rule-1".equals(f.sourceRuleId)&&f.matchedText.equals("users")));
    assertTrue(saved.getAllValues().stream().anyMatch(f->f.classification.equals("CONTRADICTED")&&"fact-1".equals(f.sourceFactId)&&f.matchedText.equals("50% lower")));
  }

  @Test void acceptingSuggestionReplacesDraftSpanAndAuditsBeforeAndAfter() {
    RuleRepository rules=mock(RuleRepository.class); FactRepository facts=mock(FactRepository.class); ReviewRepository reviews=mock(ReviewRepository.class);
    FindingRepository findings=mock(FindingRepository.class); AuditRepository audit=mock(AuditRepository.class);
    Review review=new Review("Hello users!","SOCIAL");review.id="review-2";when(reviews.findById("review-2")).thenReturn(Optional.of(review));when(reviews.save(any())).thenAnswer(i->i.getArgument(0));
    Finding finding=new Finding("review-2","TERMINOLOGY","CONTRADICTED","MEDIUM",6,11,"users","Use the preferred term.","customers","rule-1",null,"Use customers.","Voice guide","RULE");finding.id="finding-1";
    when(findings.findByIdAndReviewId("finding-1","review-2")).thenReturn(Optional.of(finding));when(findings.findByReviewId("review-2")).thenReturn(List.of(finding));
    BrandLensController controller=new BrandLensController(rules,facts,reviews,findings,audit);
    ReviewDto result=controller.accept("review-2","finding-1",null);
    assertEquals("Hello customers!",result.submittedContent());assertEquals("ACCEPTED",finding.decisionStatus);
    ArgumentCaptor<AuditEvent> event=ArgumentCaptor.forClass(AuditEvent.class);verify(audit).save(event.capture());assertEquals("Hello users!",event.getValue().oldValue);assertEquals("Hello customers!",event.getValue().newValue);
  }
}
