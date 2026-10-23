import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Activity, ArrowRight, ArrowUpRight, BookOpen, Check, ChevronRight, CircleAlert, Clock3, FileCheck2, FileText, History, Leaf, LoaderCircle, Plus, RefreshCw, ShieldCheck, X } from 'lucide-react';

const API = import.meta.env.VITE_API_BASE_URL || '';
type Rule = { id: string; ruleCode: string; title: string; category: string; description: string; severity: string; sourceReference: string; sourceQuote: string; prohibitedTerm?: string | null; preferredTerm?: string | null };
type Fact = { id: string; factKey: string; approvedValue: string; description: string; sourceReference: string; sourceQuote: string; validFrom: string; validUntil: string; expired: boolean };
type Finding = { id: string; category: string; classification: string; severity: string; startOffset: number; endOffset: number; matchedText: string; explanation: string; suggestedText?: string; sourceRuleId?: string; sourceFactId?: string; sourceQuote?: string; sourceReference?: string; engineType: string; decisionStatus: string; decisionReason?: string };
type Review = { id: string; submittedContent: string; channel: string; currentStatus: string; createdAt: string; updatedAt: string; findings: Finding[] };
type Kit = { brand: string; tagline: string; rules: Rule[]; facts: Fact[]; aiAvailable: boolean };
type Page = 'Overview' | 'New review' | 'Review' | 'Brand kit' | 'Review history';

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}/api${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try { message = (await response.json()).message || message; } catch { /* Keep the HTTP status message. */ }
    throw new Error(message);
  }
  return response.json();
}

const sample = 'Meet the LumaLeaf bottle — a smarter choice for conscious users. It has 50% lower lifecycle emissions than our old range, and 92% of families say it changed their daily habits. We think it is the most beautiful bottle on the market.';
const pages: { label: Page; icon: typeof Activity }[] = [
  { label: 'Overview', icon: Activity }, { label: 'New review', icon: Plus }, { label: 'Brand kit', icon: BookOpen }, { label: 'Review history', icon: History },
];

function classificationLabel(classification: string) {
  switch (classification) {
    case 'CONTRADICTED': return 'Contradicted';
    case 'OUTDATED': return 'Outdated';
    case 'UNSUPPORTED': return 'Needs verification';
    case 'NON_CHECKABLE': case 'NON-CHECKABLE': return 'Not checkable';
    default: return 'Review';
  }
}

export default function App() {
  const [kit, setKit] = useState<Kit | null>(null);
  const [page, setPage] = useState<Page>('Overview');
  const [review, setReview] = useState<Review | null>(null);
  const [history, setHistory] = useState<Review[]>([]);
  const [draft, setDraft] = useState('');
  const [channel, setChannel] = useState('SOCIAL');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selectedFinding, setSelectedFinding] = useState<string | null>(null);
  const draftRef = useRef<HTMLElement>(null);

  const load = async () => {
    setBusy(true); setError('');
    try { const brandKit = await api<Kit>('/brand-kit'); setKit(brandKit); setHistory(await api<Review[]>('/reviews')); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not connect to BrandLens API'); }
    finally { setBusy(false); }
  };
  useEffect(() => { void load(); }, []);
  const announce = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(''), 3200); };

  const startReview = async () => {
    if (!draft.trim()) { setError('Add draft text before running a review.'); return; }
    setBusy(true); setError('');
    try {
      const created = await api<Review>('/reviews', { method: 'POST', body: JSON.stringify({ content: draft, channel }) });
      const result = await api<Review>(`/reviews/${created.id}/analyze`, { method: 'POST' });
      setReview(result); setDraft(result.submittedContent); setPage('Review'); setSelectedFinding(null); setHistory(await api<Review[]>('/reviews'));
    } catch (e) { setError(e instanceof Error ? e.message : 'Review could not be completed'); }
    finally { setBusy(false); }
  };
  const reopen = async (id: string) => {
    setBusy(true); setError('');
    try { const result = await api<Review>(`/reviews/${id}`); setReview(result); setDraft(result.submittedContent); setPage('Review'); setSelectedFinding(null); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load review'); }
    finally { setBusy(false); }
  };
  const action = async (finding: Finding, kind: 'accept' | 'dismiss' | 'escalate') => {
    if (!review) return;
    const body: Record<string, string> = {};
    if (kind === 'dismiss') { const reason = window.prompt('Why are you dismissing this finding?'); if (!reason?.trim()) return; body.reason = reason; }
    if (kind === 'accept' && !finding.suggestedText) return;
    setBusy(true); setError('');
    try {
      const updated = await api<Review>(`/reviews/${review.id}/findings/${finding.id}/${kind}`, { method: 'POST', body: JSON.stringify(body) });
      setReview(updated); setDraft(updated.submittedContent); setHistory(await api<Review[]>('/reviews'));
      announce(kind === 'accept' ? 'Suggestion applied to the saved draft' : kind === 'dismiss' ? 'Finding dismissed' : 'Finding escalated for human verification');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save decision'); }
    finally { setBusy(false); }
  };
  const complete = async () => {
    if (!review) return;
    setBusy(true); setError('');
    try { const result = await api<Review>(`/reviews/${review.id}/complete`, { method: 'POST' }); setReview(result); setHistory(await api<Review[]>('/reviews')); announce('Review marked complete'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not complete review'); }
    finally { setBusy(false); }
  };
  const reset = async () => {
    if (!window.confirm('Reset saved demo reviews and decisions?')) return;
    setBusy(true); setError('');
    try { await api('/demo/reset', { method: 'POST' }); setReview(null); setDraft(''); setHistory([]); setPage('Overview'); announce('Demo workspace reset'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not reset demo'); }
    finally { setBusy(false); }
  };
  const navigate = (target: Page) => { setPage(target); setError(''); };
  const sampleDraft = () => { setDraft(sample); setPage('New review'); setError(''); };

  const renderDraft = (content: string, findings: Finding[]) => {
    const matches = findings.flatMap(f => {
      const at = content.indexOf(f.matchedText);
      return at < 0 ? [] : [{ finding: f, start: at, end: at + f.matchedText.length }];
    }).sort((a, b) => a.start - b.start);
    const pieces: ReactNode[] = [];
    let cursor = 0;
    matches.forEach(({ finding, start, end }) => {
      if (start < cursor) return;
      pieces.push(content.slice(cursor, start));
      pieces.push(<mark key={finding.id} id={`claim-${finding.id}`} className={`draft-mark ${finding.classification.toLowerCase().replaceAll('_', '-')}${selectedFinding === finding.id ? ' selected' : ''}`} onClick={() => setSelectedFinding(finding.id)} title={`${classificationLabel(finding.classification)} finding`}>{content.slice(start, end)}<sup>{String(findings.indexOf(finding) + 1).padStart(2, '0')}</sup></mark>);
      cursor = end;
    });
    pieces.push(content.slice(cursor));
    return pieces;
  };
  const focusClaim = (finding: Finding) => {
    setSelectedFinding(finding.id);
    document.getElementById(`claim-${finding.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    draftRef.current?.focus({ preventScroll: true });
  };
  const sourceTitle = (finding: Finding) => finding.sourceRuleId
    ? kit?.rules.find(rule => rule.id === finding.sourceRuleId)?.title || 'Brand guideline'
    : finding.sourceFactId ? kit?.facts.find(fact => fact.id === finding.sourceFactId)?.factKey.replaceAll('_', ' ') || 'Approved fact' : 'Approved source';

  return <div className="shell">
    <aside className="sidebar">
      <button className="brand" onClick={() => navigate('Overview')} aria-label="BrandLens overview"><span className="brandmark"><Leaf size={19}/></span><span><b>brandlens</b><small>LUMALEAF WORKSPACE</small></span></button>
      <button className="sidebar-create" onClick={() => { setDraft(''); navigate('New review'); }}><Plus size={16}/>New review</button>
      <p className="nav-label">WORKSPACE</p>
      <nav aria-label="Workspace navigation">{pages.filter(item => item.label !== 'New review').map(({ label, icon: Icon }) => <button key={label} className={`navitem ${page === label ? 'active' : ''}`} onClick={() => navigate(label)}><Icon size={17}/><span>{label}</span>{label === 'Review history' && history.length > 0 && <span className="nav-count">{history.length}</span>}</button>)}</nav>
      <div className="side-bottom"><div className="side-status"><span className="green-dot"/>Demo workspace</div><button className="text-button reset-button" onClick={reset} disabled={busy}><RefreshCw size={14}/>Reset demo data</button></div>
    </aside>

    <main className="main">
      <header className="topbar"><div className="crumb"><span>LumaLeaf</span><ChevronRight size={14}/><b>{page === 'Review' ? 'Review findings' : page}</b></div><div className="top-actions"><span className="saved"><span className="green-dot"/>Saved to workspace</span><span className="avatar small" aria-label="Demo reviewer">DR</span></div></header>
      <div className="content">
        {error && <div className="banner error" role="alert"><CircleAlert size={17}/><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss error"><X size={15}/></button></div>}
        {notice && <div className="banner success" role="status"><Check size={17}/><span>{notice}</span></div>}
        {busy && <div className="loading" role="status"><LoaderCircle size={17}/> {page === 'New review' ? 'Reviewing draft…' : 'Saving changes…'}</div>}

        {page === 'Overview' && <>
          <div className="page-heading"><div><div className="eyebrow">BRAND REVIEW WORKSPACE</div><h1>Keep the next draft on brand.</h1><p className="subheading">Check campaign copy against LumaLeaf’s approved language and facts.</p></div><button className="primary" onClick={sampleDraft}><Plus size={17}/>Start a review</button></div>
          <section className="start-card"><div className="start-copy"><span className="eyebrow">YOUR NEXT STEP</span><h2>Review a campaign draft</h2><p>Paste copy, choose a channel, and see each finding alongside the approved source that prompted it.</p><div className="overview-actions"><button className="primary" onClick={() => { setDraft(''); navigate('New review'); }}><Plus size={16}/>Write or paste a draft</button><button className="secondary" onClick={sampleDraft}>Try sample draft <ArrowUpRight size={15}/></button></div></div><div className="overview-note"><ShieldCheck size={19}/><div><b>Evidence before judgment</b><p>Unsupported claims are sent for human verification. They are not treated as false without conflicting approved evidence.</p></div></div></section>
          <div className="section-head kit-head"><div><h2>Available review sources</h2><p>Brand guidance and approved facts used for checks.</p></div><button className="link-button" onClick={() => navigate('Brand kit')}>Open brand kit <ArrowUpRight size={15}/></button></div>
          <div className="overview-grid"><div className="mini-card"><span className="mini-icon teal"><BookOpen size={17}/></span><div><b>{kit?.rules.length ?? '—'} guidelines</b><small>Language and brand rules</small></div></div><div className="mini-card"><span className="mini-icon blue"><FileCheck2 size={17}/></span><div><b>{kit?.facts.length ?? '—'} approved facts</b><small>Claims with source references</small></div></div><div className="mini-card"><span className="mini-icon amber"><Clock3 size={17}/></span><div><b>{history.length} saved reviews</b><small>Open review history</small></div></div></div>
        </>}

        {page === 'New review' && <>
          <div className="page-heading"><div><div className="eyebrow">CONTENT REVIEW <span>·</span> STEP 1 OF 2</div><h1>Review a draft</h1><p className="subheading">Add campaign copy and choose where it will appear.</p></div></div>
          <div className="compose-grid"><section className="panel editor-panel"><div className="panel-title"><div><FileText size={18}/><b>Campaign draft</b></div><span>{draft.length.toLocaleString()} / 12,000</span></div><label className="field-label" htmlFor="channel">Publishing channel</label><select id="channel" value={channel} onChange={event => setChannel(event.target.value)}><option value="SOCIAL">Social media</option><option value="WEBSITE">Website</option><option value="EMAIL">Email</option><option value="ADVERTISEMENT">Advertisement</option></select><label className="field-label draft-label" htmlFor="draft">Draft text</label><textarea id="draft" maxLength={12000} placeholder="Paste or write campaign copy here…" value={draft} onChange={event => setDraft(event.target.value)}/><div className="editor-footer"><span><span className="green-dot"/>Draft is saved when you run a review</span><button className="text-button" onClick={() => setDraft(sample)}>Use sample draft <ArrowRight size={14}/></button></div></section>
            <section className="panel scope-panel"><div className="panel-title"><div><ShieldCheck size={18}/><b>What we check</b></div></div><p className="scope-desc">Findings are based on these approved sources in the LumaLeaf brand kit.</p>{kit?.rules?.slice(0, 3).map(rule => <div key={rule.id} className="scope-row"><span className="scope-check"><Check size={12}/></span><div><b>{rule.title}</b><small>{rule.category.toLowerCase()}</small></div></div>)}<div className="scope-divider"/><div className="scope-row"><span className="scope-check"><Check size={12}/></span><div><b>Approved facts</b><small>{kit?.facts.length ?? 0} claims with sources and validity dates</small></div></div><div className="ai-note"><ShieldCheck size={16}/><span>{kit?.aiAvailable ? 'AI-assisted checks are enabled. Draft text and relevant sources may be sent to your configured provider.' : 'Rule-based checks are ready. AI-assisted checks are unavailable.'}</span></div><button className="primary full" onClick={startReview} disabled={busy || !draft.trim()}><ShieldCheck size={17}/>{busy ? 'Reviewing…' : 'Run brand review'}<ArrowUpRight size={16}/></button><div className="privacy-note">Your copy is not published. Suggestions only change it when accepted.</div></section></div>
        </>}

        {page === 'Review' && review && <>
          <div className="page-heading review-heading"><div><div className="eyebrow">REVIEW RESULTS <span>·</span> {review.channel}</div><h1>Findings for this draft</h1><p className="subheading">Select a finding to locate its claim in the draft and review the evidence.</p></div><button className={`secondary ${review.currentStatus === 'COMPLETE' ? 'completed' : ''}`} onClick={complete} disabled={busy || review.currentStatus === 'COMPLETE'}>{review.currentStatus === 'COMPLETE' ? <Check size={16}/> : <FileCheck2 size={16}/>} {review.currentStatus === 'COMPLETE' ? 'Review complete' : 'Complete review'}</button></div>
          <div className="review-summary"><b>{review.findings.length} {review.findings.length === 1 ? 'finding' : 'findings'}</b><span className="summary-divider"/><span>{review.findings.filter(f => f.decisionStatus === 'OPEN').length} need a decision</span><span className="summary-right"><span className="green-dot"/>{review.currentStatus === 'COMPLETE' ? 'Complete' : 'Saved'}</span></div>
          <div className="review-layout"><section className="panel draft-panel" ref={draftRef} tabIndex={-1}><div className="panel-title"><div><FileText size={18}/><b>Reviewed draft</b></div><span className="readonly-label">{review.channel}</span></div><p className="draft-instruction">Highlighted text corresponds to a finding. Select a highlight or finding to follow the link.</p><div className="draft-body">{renderDraft(review.submittedContent, review.findings)}</div><div className="draft-legend"><span><i className="legend-mark contradicted"/>Contradicted</span><span><i className="legend-mark unsupported"/>Verify</span><span><i className="legend-mark outdated"/>Outdated</span><span><i className="legend-mark non-checkable"/>Not checkable</span></div><div className="draft-footer"><span>{review.submittedContent.length} characters</span><span>Saved {new Date(review.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span></div></section>
            <section className="findings-list"><div className="findings-head"><div><h2>Review findings</h2><p>Each item includes its source and next step.</p></div><span className="count-pill">{review.findings.filter(f => f.decisionStatus === 'OPEN').length} open</span></div>
              {review.findings.length === 0 && <div className="empty panel"><Check size={21}/><b>No findings</b><span>This draft matched the configured checks.</span></div>}
              {review.findings.map((finding, index) => <article className={`finding-card ${selectedFinding === finding.id ? 'selected' : ''}`} key={finding.id} id={`finding-${finding.id}`}>
                <div className="finding-top"><span className={`severity-label ${finding.severity.toLowerCase()}`}><i/>{finding.severity.toLowerCase()} priority</span><span className="category-label">{finding.category.toLowerCase()}</span><span className="finding-number">{String(index + 1).padStart(2, '0')}</span></div>
                <button className="finding-quote" onClick={() => focusClaim(finding)} aria-label={`Show claim in draft: ${finding.matchedText}`}><span>Claim in draft</span><q>{finding.matchedText}</q><ArrowRight size={14}/></button>
                <div className="classification"><span className={`class-tag ${finding.classification.toLowerCase().replaceAll('_', '-')}`}>{classificationLabel(finding.classification)}</span><span className="engine">{finding.engineType === 'RULE' ? 'Rule-based check' : 'AI-assisted check'}</span></div>
                <p className="finding-explanation">{finding.explanation}</p>
                <div className={`source-box ${finding.sourceQuote ? '' : 'no-source'}`}><div className="source-heading"><BookOpen size={14}/><b>{finding.sourceQuote ? sourceTitle(finding) : 'No approved source found'}</b></div>{finding.sourceQuote ? <><div className="source-ref">{finding.sourceReference || 'Approved brand source'}</div><blockquote>“{finding.sourceQuote}”</blockquote></> : <p className="source-missing">This claim needs a person to verify it before it can be treated as supported or contradicted.</p>}</div>
                {finding.suggestedText && <div className="suggestion"><span>Suggested wording</span><p><del>{finding.matchedText}</del><ArrowRight/><strong>{finding.suggestedText}</strong></p></div>}
                {finding.decisionStatus !== 'OPEN' ? <div className={`decision-state ${finding.decisionStatus.toLowerCase()}`}><Check size={15}/><div><b>{finding.decisionStatus === 'ACCEPTED' ? 'Suggestion applied to draft' : finding.decisionStatus === 'DISMISSED' ? 'Finding dismissed' : 'Escalated for human review'}</b>{finding.decisionReason && <span>{finding.decisionReason}</span>}</div></div> : <div className="finding-actions">{finding.suggestedText && <button className="accept" onClick={() => action(finding, 'accept')} disabled={busy}><Check size={15}/>Accept suggestion</button>}<button className="dismiss" onClick={() => action(finding, 'dismiss')} disabled={busy}>Dismiss</button><button className="escalate" onClick={() => action(finding, 'escalate')} disabled={busy}>Escalate for review</button></div>}
              </article>)}
            </section></div>
        </>}

        {page === 'Brand kit' && <>
          <div className="page-heading"><div><div className="eyebrow">APPROVED SOURCES</div><h1>Brand kit</h1><p className="subheading">The rules and facts BrandLens uses to review LumaLeaf content.</p></div><span className="demo-pill">DEMO BRAND</span></div>
          <div className="kit-banner"><span className="brandmark"><Leaf size={19}/></span><div><b>{kit?.brand || 'LumaLeaf'}</b><p>{kit?.tagline}</p></div><span className="active-pill"><span className="green-dot"/>{kit?.rules.length || 0} active rules</span></div>
          <div className="section-head"><div><h2>Brand guidelines</h2><p>Rules with source excerpts and severity.</p></div></div><div className="rule-list">{kit?.rules.map(rule => <article className="rule-card" key={rule.id}><div className="rule-code">{rule.ruleCode}</div><div className="rule-main"><div className="rule-title"><b>{rule.title}</b><span className="active-pill"><span className="green-dot"/>Active</span></div><div className="rule-meta"><span>{rule.category}</span><i/><span className={`severity-label ${rule.severity.toLowerCase()}`}><i/>{rule.severity.toLowerCase()} priority</span></div><p>{rule.description}</p><div className="rule-source"><BookOpen size={14}/><span>“{rule.sourceQuote}”</span><small>{rule.sourceReference}</small></div>{rule.prohibitedTerm && <div className="term-pair"><span className="bad-term">Avoid: {rule.prohibitedTerm}</span><ArrowRight/><span className="good-term">Prefer: {rule.preferredTerm}</span></div>}</div></article>)}</div>
          <div className="section-head facts-head"><div><h2>Approved facts</h2><p>Time-bound claims. Expired facts prompt verification.</p></div></div><div className="facts-grid">{kit?.facts.map(fact => <article className={`fact-card ${fact.expired ? 'expired' : ''}`} key={fact.id}><div className="fact-top"><span className="fact-icon"><FileCheck2 size={17}/></span><span className={fact.expired ? 'expired-pill' : 'active-pill'}>{fact.expired ? 'Expired' : 'Current'}</span></div><h3>{fact.approvedValue}</h3><p>{fact.description}</p><div className="fact-source"><b>{fact.factKey.replaceAll('_', ' ')}</b><span>“{fact.sourceQuote}”</span><small>{fact.sourceReference}</small><small>Valid {fact.validFrom} — {fact.validUntil}</small></div></article>)}</div>
        </>}

        {page === 'Review history' && <>
          <div className="page-heading"><div><div className="eyebrow">GOVERNANCE LOG</div><h1>Review history</h1><p className="subheading">Saved drafts and their recorded decisions.</p></div><button className="primary" onClick={sampleDraft}><Plus size={16}/>New review</button></div>
          <section className="panel history-panel"><div className="history-head"><div><History size={18}/><b>Saved reviews</b></div><span>{history.length} total</span></div>{history.length === 0 ? <div className="history-empty"><FileText size={24}/><b>No reviews yet</b><span>Run a review to see its findings here.</span><button className="primary" onClick={sampleDraft}><Plus size={15}/>Start a review</button></div> : <div className="history-table"><div className="table-row table-head"><span>REVIEW</span><span>CHANNEL</span><span>FINDINGS</span><span>STATUS</span><span>CREATED</span><span/></div>{history.map(item => <button className="table-row" key={item.id} onClick={() => reopen(item.id)} disabled={busy}><span><b>Brand review</b><small>#{item.id.slice(0, 8)}</small></span><span>{item.channel.toLowerCase()}</span><span>{item.findings.length} findings</span><span><span className={`status-pill ${item.currentStatus.toLowerCase()}`}><i/>{item.currentStatus === 'COMPLETE' ? 'Complete' : 'In review'}</span></span><span>{new Date(item.createdAt).toLocaleDateString()}</span><ChevronRight size={16}/></button>)}</div>}</section>
        </>}
      </div>
    </main>
  </div>;
}
