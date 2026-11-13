import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';

const agent = 'JobMonitorBot';
const dir = resolve(process.env.JOB_MONITOR_DIR || 'job-monitor/data');
const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date());
const delay = ms => new Promise(r => setTimeout(r, ms));
const clean = s => String(s || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;|&#160;/gi, ' ').replace(/&amp;/gi, '&').replace(/\s+/g, ' ').trim();
const id = s => createHash('sha256').update(s).digest('hex').slice(0, 20);
const restricted = s => /验证码|安全验证|访问过于频繁|请先登录|captcha|access denied/i.test(s);

async function get(url, redirects = 0) {
  if (redirects > 4) throw Error('too many redirects');
  const u = new URL(url);
  if (!['http:', 'https:'].includes(u.protocol)) throw Error('unsupported URL');
  const res = await fetch(u, { headers: { 'User-Agent': agent }, redirect: 'manual', signal: AbortSignal.timeout(15000) });
  if ([301, 302, 303, 307, 308].includes(res.status)) {
    const next = new URL(res.headers.get('location'), u);
    if (next.origin !== u.origin || /login|passport|security|captcha|verify/i.test(next.pathname)) throw Error('redirect requires review');
    if (!await allowed(next.href)) throw Error('redirect disallowed by robots.txt');
    return get(next.href, redirects + 1);
  }
  if (!res.ok) throw Error(`HTTP ${res.status}`);
  const text = await res.text();
  if (restricted(text)) throw Error('login, verification, or access restriction detected');
  return text;
}

const rulesCache = new Map();
async function allowed(url) {
  const u = new URL(url);
  if (!rulesCache.has(u.origin)) {
    const res = await fetch(new URL('/robots.txt', u), { headers: { 'User-Agent': agent }, redirect: 'manual', signal: AbortSignal.timeout(10000) });
    if (res.status === 404 || res.status === 410) rulesCache.set(u.origin, []);
    else if (!res.ok) throw Error(`robots.txt unavailable: HTTP ${res.status}`);
    else rulesCache.set(u.origin, parseRobots(await res.text()));
  }
  const rules = rulesCache.get(u.origin).filter(r => (u.pathname + u.search).startsWith(r.path));
  rules.sort ( (a,b) => b.path.length - a.path.length || Number(b.allow) - Number(a.allow));
  return !rules.length || rules[0].allow;
}
function parseRobots(text) {
  const groups = []; let group = null; let last = '';
  for (const raw of text.split(/\r?\n/)) {
    const m = raw.replace(/#.*/, '').match(/^\s*([^:]+):\s*(.*?)\s*$/); if (!m) continue;
    const key = m[1].toLowerCase(), value = m[2];
    if (key === 'user-agent') {
      if (!group || last !== 'user-agent') { group = { agents: [], rules: [] }; groups.push(group); }
      group.agents.push(value.toLowerCase());
    } else if (group && ['allow', 'disallow'].includes(key) && value) {
      // Unsupported wildcard rules are treated conservatively as disallow.
      group.rules.push({ allow: key === 'allow' && !/[*$]/.test(value), path: value.split(/[*$]/)[0] });
    }
    last = key;
  }
  const specific = groups.filter(g => g.agents.some(a => a !== '*' && agent.toLowerCase().includes(a)));
  return (specific.length ? specific : groups.filter(g => g.agents.includes('*'))).flatMap(g => g.rules);
}
function jsonJobs(html) {
  const jobs = [];
  function walk(x) {
    if (!x || typeof x !== 'object') return;
    if (Array.isArray(x)) { x.forEach(walk); return; }
    if ([x['@type']].flat().includes('JobPosting')) jobs.push(x);
    for (const v of Object.values(x)) if (typeof v === 'object') walk(v);
  }
  for (const m of html.matchAll(/<script\b[^>]*type\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script\s*>/gi)) {
    try { walk(JSON.parse(m[1])); } catch { /* malformed metadata is not evidence */ }
  }
  return jobs;
}
function normalize(job, url) {
  const salary = job.baseSalary || {};
  const value = salary.value || {};
  const location = [job.jobLocation].flat().filter(Boolean).map(x => Object.values(x.address || {}).filter(v => typeof v === 'string').join(' ')).join('; ');
  const description = clean(job.description);
  const keywords = ['Java','Spring Boot','Spring Cloud','MySQL','Redis','Kafka','Python','RAG','Agent','LLM','大模型','微服务','向量数据库'].filter(k => description.toLowerCase().includes(k.toLowerCase()));
  return { id: id(url), url, company: clean(job.hiringOrganization?.name), title: clean(job.title), location, salary: { min: value.minValue ?? value.value ?? null, max: value.maxValue ?? value.value ?? null, currency: salary.currency || '', unit: value.unitText || '' }, education: clean(job.educationRequirements?.credentialCategory || job.educationRequirements), experience: clean(job.experienceRequirements?.monthsOfExperience ? `${job.experienceRequirements.monthsOfExperience} months` : job.experienceRequirements), keywords, responsibilities: description, observedDate: date, validThrough: job.validThrough || '', status: '待核实', evidence: '公开页面的结构化职位数据；招聘有效性未确认' };
}
async function load(path, fallback) { try { return JSON.parse(await readFile(path, 'utf8')); } catch (e) { if (e.code === 'ENOENT') return fallback; throw e; } }
async function main() {
  const urls = [...new Set(process.argv.slice(2))];
  if (!urls.length) throw Error('用法：node job-monitor/monitor.mjs <公开职位URL> [更多URL]');
  await mkdir(dir, { recursive: true });
  const path = resolve(dir, `${date}.json`), records = await load(path, []);
  for (const url of urls) {
    let result;
    try {
      if (!await allowed(url)) throw Error('robots.txt disallows access');
      const html = await get(url), jobs = jsonJobs(html);
      result = jobs.length ? jobs.map(j => normalize(j, url)) : [{ id: id(url), url, observedDate: date, status: '待核实', error: '页面没有可识别的 JobPosting 数据' }];
    } catch (e) { result = [{ id: id(url), url, observedDate: date, status: '待核实', error: e.message }]; }
    for (const row of result) { const i = records.findIndex(x => x.id === row.id); if (i >= 0) records[i] = row; else records.push(row); }
    console.log(JSON.stringify(result));
    await writeFile(path, JSON.stringify(records, null, 2), 'utf8');
    await delay(2000);
  }
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
