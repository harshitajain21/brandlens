package com.brandlens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.*;
import java.util.*;
import java.util.regex.*;

record CreateReview(@NotBlank @Size(max=12000) String content,@NotBlank @Size(max=40) String channel){}
record Decision(@Size(max=2000) String reason,@Size(max=2000) String suggestedText){}
record RuleDto(String id,String ruleCode,String title,String category,String description,String severity,String sourceReference,String sourceQuote,String prohibitedTerm,String preferredTerm,boolean active){}
record FactDto(String id,String factKey,String approvedValue,String description,String sourceReference,String sourceQuote,LocalDate validFrom,LocalDate validUntil,boolean active,boolean expired){}
record FindingDto(String id,String category,String classification,String severity,int startOffset,int endOffset,String matchedText,String explanation,String suggestedText,String sourceRuleId,String sourceFactId,String sourceQuote,String sourceReference,String engineType,String decisionStatus,String decisionReason){}
record ReviewDto(String id,String submittedContent,String channel,String currentStatus,java.time.Instant createdAt,java.time.Instant updatedAt,List<FindingDto> findings){}
record EventDto(String id,String action,String findingId,String oldValue,String newValue,String reason,String actor,Instant timestamp){}
record KitDto(String brand,String tagline,List<RuleDto> rules,List<FactDto> facts,boolean aiAvailable){}

@Configuration class SeedConfig {
 @Bean CommandLineRunner seed(RuleRepository rules,FactRepository facts){return args->{
  if(rules.count()==0){rules.saveAll(List.of(
   new BrandRule("VOC-01","Use welcoming language","TERMINOLOGY","Avoid the exclusionary term; use the preferred alternative.","MEDIUM","LumaLeaf Voice Guide v1, p. 2","Use ‘customers’ in all customer-facing copy; avoid ‘users’.","users","customers"),
   new BrandRule("TONE-01","Keep claims grounded","TONE","Use clear, warm language and avoid unsubstantiated superlatives.","LOW","LumaLeaf Voice Guide v1, p. 3","Prefer specific, evidenced benefits over broad superlatives.",null,null),
   new BrandRule("ACCESS-01","Describe image content","ACCESSIBILITY","Images should have meaningful alt text.","LOW","LumaLeaf Accessibility Notes v1","Provide concise alt text for informative images.",null,null)));}
  if(facts.count()==0){facts.saveAll(List.of(
   new ApprovedFact("carbon_reduction","30%","Verified lifecycle emissions reduction for the current product.","Impact Report 2026, p. 4","The LumaLeaf bottle has 30% lower lifecycle emissions than our 2024 baseline.",LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)),
   new ApprovedFact("capacity_ml","500 ml","Bottle capacity.","Product Spec Sheet v2","Bottle capacity: 500 ml.",LocalDate.of(2026,3,1),LocalDate.of(2027,3,1)),
   new ApprovedFact("legacy_recycled_pct","80% recycled material","Superseded legacy packaging statement.","Packaging Brief 2024 (expired)","Old bottle prototype: 80% recycled material.",LocalDate.of(2024,1,1),LocalDate.of(2024,12,31))));}
 };}
}

@RestController @RequestMapping("/api") class BrandLensController {
 private final RuleRepository rules; private final FactRepository facts; private final ReviewRepository reviews; private final FindingRepository findings; private final AuditRepository audit;
 @Value("${brandlens.ai-key:}") String aiKey;
 BrandLensController(RuleRepository r,FactRepository f,ReviewRepository v,FindingRepository d,AuditRepository a){rules=r;facts=f;reviews=v;findings=d;audit=a;}
 @GetMapping("/health") Map<String,Object> health(){rules.count();return Map.of("status","ok","database","connected");}
 @GetMapping("/brand-kit") KitDto kit(){return new KitDto("LumaLeaf","Small choices. A lighter footprint.",rules.findByActiveTrue().stream().map(this::dto).toList(),facts.findByActiveTrue().stream().map(this::dto).toList(),false);}
 private RuleDto dto(BrandRule r){return new RuleDto(r.id,r.ruleCode,r.title,r.category,r.description,r.severity,r.sourceReference,r.sourceQuote,r.prohibitedTerm,r.preferredTerm,r.active);}
 private FactDto dto(ApprovedFact f){return new FactDto(f.id,f.factKey,f.approvedValue,f.description,f.sourceReference,f.sourceQuote,f.validFrom,f.validUntil,f.active,f.validUntil!=null&&f.validUntil.isBefore(LocalDate.now()));}
 private FindingDto dto(Finding f){return new FindingDto(f.id,f.category,f.classification,f.severity,f.startOffset,f.endOffset,f.matchedText,f.explanation,f.suggestedText,f.sourceRuleId,f.sourceFactId,f.sourceQuote,f.sourceReference,f.engineType,f.decisionStatus,f.decisionReason);}
 private ReviewDto dto(Review r){return new ReviewDto(r.id,r.submittedContent,r.channel,r.currentStatus,r.createdAt,r.updatedAt,findings.findByReviewId(r.id).stream().map(this::dto).toList());}
 @GetMapping("/reviews") List<ReviewDto> list(){return reviews.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList();}
 @PostMapping("/reviews") ResponseEntity<ReviewDto> create(@Valid @RequestBody CreateReview req){Review r=reviews.save(new Review(req.content(),req.channel()));return ResponseEntity.status(201).body(dto(r));}
 @GetMapping("/reviews/{id}") ReviewDto get(@PathVariable String id){return dto(review(id));}
 @PostMapping("/reviews/{id}/analyze") ReviewDto analyze(@PathVariable String id){Review r=review(id);findings.deleteAll(findings.findByReviewId(id));String text=r.submittedContent;
  for(BrandRule rule:rules.findByActiveTrue()) if(rule.prohibitedTerm!=null&&!rule.prohibitedTerm.isBlank()){Matcher m=Pattern.compile("(?i)\\b"+Pattern.quote(rule.prohibitedTerm)+"\\b").matcher(text);while(m.find())findings.save(new Finding(id,rule.category,"CONTRADICTED",rule.severity,m.start(),m.end(),m.group(),rule.description,rule.preferredTerm,rule.id,null,rule.sourceQuote,rule.sourceReference,"RULE"));}
  List<ApprovedFact> approvedFacts=facts.findByActiveTrue();
  for(ApprovedFact fact:approvedFacts){
   if(fact.validUntil!=null&&fact.validUntil.isBefore(LocalDate.now())){Matcher m=Pattern.compile("(?i)\\b"+Pattern.quote(fact.approvedValue)+"\\b").matcher(text);while(m.find())findings.save(new Finding(id,"FACT","OUTDATED","MEDIUM",m.start(),m.end(),m.group(),"This statement relies on an expired approved fact; verify against current evidence.",null,null,fact.id,fact.sourceQuote,fact.sourceReference,"RULE"));continue;}
   if(fact.factKey.equals("carbon_reduction")&&Pattern.compile("(?i)\\b(\\d{1,3})\\s*%\\s*(?:lower|less)\\b").matcher(text).find()) {Matcher m=Pattern.compile("(?i)\\b(\\d{1,3})\\s*%\\s*(?:lower|less)\\b").matcher(text);while(m.find())if(!m.group(1).equals("30"))findings.save(new Finding(id,"FACT","CONTRADICTED","HIGH",m.start(),m.end(),m.group(),"The number conflicts with the current approved lifecycle emissions figure.","30% lower",null,fact.id,fact.sourceQuote,fact.sourceReference,"RULE"));}
  }
  // Quantified claims need an applicable, current approved source. Unsupported means unverified, never false.
  BrandRule substantiation=rules.findByActiveTrue().stream().filter(x->x.ruleCode.equals("TONE-01")).findFirst().orElse(null);
  Matcher quantified=Pattern.compile("(?i)\\b\\d+(?:[,.]\\d+)?\\s*(?:%|percent\\b|ml\\b|l\\b|litres?\\b|liters?\\b|kg\\b|g\\b|mg\\b|cm\\b|mm\\b|hours?\\b|days?\\b|weeks?\\b|months?\\b|years?\\b)(?:(?!\\s+(?:and|but|while)\\b)[^.!?])*").matcher(text);
  while(quantified.find()){
   String claim=quantified.group();
   boolean contradictedByCurrentFact=approvedFacts.stream().filter(f->f.factKey.equals("carbon_reduction")&&(f.validUntil==null||!f.validUntil.isBefore(LocalDate.now())))
    .anyMatch(f->Pattern.compile("(?i)\\b\\d{1,3}\\s*%\\s*(?:lower|less)\\b").matcher(claim).find()&&!claim.matches("(?is).*\\b"+Pattern.quote(f.approvedValue.replace("%",""))+"\\s*%\\s*(?:lower|less)\\b.*"));
   if(contradictedByCurrentFact||hasApplicableApprovedStatistic(claim,approvedFacts))continue;
   findings.save(new Finding(id,"CLAIM","UNSUPPORTED","MEDIUM",quantified.start(),quantified.end(),claim,"No applicable approved source was found for this quantitative claim. Verify it or provide an approved source.",null,substantiation==null?null:substantiation.id,null,substantiation==null?null:substantiation.sourceQuote,substantiation==null?null:substantiation.sourceReference,"RULE"));
  }
  r.updatedAt=Instant.now();reviews.save(r);return dto(r);}
 @PostMapping("/reviews/{reviewId}/findings/{findingId}/accept") ReviewDto accept(@PathVariable String reviewId,@PathVariable String findingId,@RequestBody(required=false) Decision decision){Review r=review(reviewId);Finding f=finding(reviewId,findingId);if(f.suggestedText==null||f.suggestedText.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No suggestion available");String replacement=decision!=null&&decision.suggestedText()!=null&&!decision.suggestedText().isBlank()?decision.suggestedText():f.suggestedText;String before=r.submittedContent;if(f.startOffset<0||f.endOffset>before.length()||!before.substring(f.startOffset,f.endOffset).equals(f.matchedText))throw new ResponseStatusException(HttpStatus.CONFLICT,"Finding span no longer matches draft");r.submittedContent=before.substring(0,f.startOffset)+replacement+before.substring(f.endOffset);r.updatedAt=Instant.now();reviews.save(r);f.decisionStatus="ACCEPTED";findings.save(f);audit.save(new AuditEvent(reviewId,findingId,"ACCEPT",before,r.submittedContent,null));return dto(r);}
 @PostMapping("/reviews/{reviewId}/findings/{findingId}/dismiss") ReviewDto dismiss(@PathVariable String reviewId,@PathVariable String findingId,@Valid @RequestBody Decision d){Review r=review(reviewId);Finding f=finding(reviewId,findingId);if(d.reason()==null||d.reason().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A dismissal reason is required");f.decisionStatus="DISMISSED";f.decisionReason=d.reason();findings.save(f);audit.save(new AuditEvent(reviewId,findingId,"DISMISS",f.matchedText,f.matchedText,d.reason()));r.updatedAt=Instant.now();reviews.save(r);return dto(r);}
 @PostMapping("/reviews/{reviewId}/findings/{findingId}/escalate") ReviewDto escalate(@PathVariable String reviewId,@PathVariable String findingId,@RequestBody(required=false) Decision d){Review r=review(reviewId);Finding f=finding(reviewId,findingId);f.decisionStatus="ESCALATED";f.decisionReason=d==null?null:d.reason();findings.save(f);audit.save(new AuditEvent(reviewId,findingId,"ESCALATE",f.matchedText,f.matchedText,f.decisionReason));r.updatedAt=Instant.now();reviews.save(r);return dto(r);}
 @PostMapping("/reviews/{id}/complete") ReviewDto complete(@PathVariable String id){Review r=review(id);boolean blockers=findings.findByReviewId(id).stream().anyMatch(f->f.decisionStatus.equals("OPEN")&&(f.severity.equals("HIGH")||f.classification.equals("CONTRADICTED")));if(blockers)throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolve, dismiss, or escalate confirmed contradictions before completing this review");r.currentStatus="COMPLETE";r.updatedAt=Instant.now();reviews.save(r);audit.save(new AuditEvent(id,null,"COMPLETE", "IN_REVIEW","COMPLETE",null));return dto(r);}
 @GetMapping("/reviews/{id}/events") List<EventDto> events(@PathVariable String id){review(id);return audit.findByReviewIdOrderByTimestampAsc(id).stream().map(e->new EventDto(e.id,e.action,e.findingId,e.oldValue,e.newValue,e.reason,e.actor,e.timestamp)).toList();}
 @PostMapping("/demo/reset") Map<String,String> reset(){findings.deleteAll();audit.deleteAll();reviews.deleteAll();return Map.of("status","ready");}
 private Review review(String id){return reviews.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found"));}
 private Finding finding(String reviewId,String id){return findings.findByIdAndReviewId(id,reviewId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Finding not found"));}
 private boolean hasApplicableApprovedStatistic(String claim,List<ApprovedFact> approvedFacts){
  Matcher quantity=Pattern.compile("(?i)\\b\\d+(?:[,.]\\d+)?\\s*(?:%|percent\\b|ml\\b|l\\b|litres?\\b|liters?\\b|kg\\b|g\\b|mg\\b|cm\\b|mm\\b|hours?\\b|days?\\b|weeks?\\b|months?\\b|years?\\b)").matcher(claim);
  if(!quantity.find())return false;
  String value=quantity.group();Set<String> claimTerms=new HashSet<>();Matcher words=Pattern.compile("(?i)\\b[a-z]{4,}\\b").matcher(claim);
  while(words.find())claimTerms.add(words.group().toLowerCase(Locale.ROOT));
  claimTerms.removeAll(Set.of("this","that","with","from","have","than","more","over","people","families","customers","their","they","them","say","says"));
  for(ApprovedFact fact:approvedFacts){
   if(!fact.active||(fact.validUntil!=null&&fact.validUntil.isBefore(LocalDate.now())))continue;
   String evidence=(Objects.toString(fact.approvedValue,"")+" "+Objects.toString(fact.sourceQuote,"")).toLowerCase(Locale.ROOT);
   if(!evidence.contains(value.toLowerCase(Locale.ROOT)))continue;
   Matcher evidenceWords=Pattern.compile("(?i)\\b[a-z]{4,}\\b").matcher(evidence);
   while(evidenceWords.find())if(claimTerms.contains(evidenceWords.group().toLowerCase(Locale.ROOT)))return true;
  }
  return false;
 }
}
@Configuration class CorsConfig {
 @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer cors(@Value("${brandlens.cors-origins:http://localhost:5173}") String origins){return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer(){public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry r){r.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET","POST","OPTIONS").allowedHeaders("*");}};}
}
